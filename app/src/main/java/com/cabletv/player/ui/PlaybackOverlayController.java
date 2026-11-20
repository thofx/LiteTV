package com.cabletv.player.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.cabletv.player.R;
import com.cabletv.player.epg.EpgManager;
import com.cabletv.player.model.Channel;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 全屏播放时的三块浮层状态，从 MainActivity 拆出：
 * <ul>
 *   <li>暂停：是否暂停、暂停的那一刻（暂停中按左键回退以它为基准），HUD 常驻；</li>
 *   <li>换台卡片：左上角的频道 + 当前节目；</li>
 *   <li>缓冲网速：缓冲期间每秒刷新屏幕中央的网速。</li>
 * </ul>
 * 也负责 Activity 前后台切换时这三者的处理，避免暂停状态与播放器不一致。
 */
final class PlaybackOverlayController {

    private static final long BUFFERING_TICK_MS = 1000;

    private final Context mContext;
    private final VideoView mVideo;
    private final TimeShiftBarComponent mHud;
    private final ZapCardView mZapCard;
    private final PlaybackStatusView mStatus;
    private final EpgManager mEpg;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mBufferingTick = this::tickBuffering;

    /** 暂停的那一刻（epoch ms）；0 表示没有暂停。 */
    private long mPausedAtMs;

    PlaybackOverlayController(Context context, VideoView video, TimeShiftBarComponent hud,
            ZapCardView zapCard, PlaybackStatusView status, EpgManager epg) {
        mContext = context;
        mVideo = video;
        mHud = hud;
        mZapCard = zapCard;
        mStatus = status;
        mEpg = epg;
    }

    // ---------------- 暂停 ----------------

    boolean isPaused() {
        return mPausedAtMs > 0;
    }

    long pausedAtMs() {
        return mPausedAtMs;
    }

    /**
     * 暂停播放，HUD 显示「已暂停」并常驻。
     *
     * @param pausedAtMs 暂停对应的节目时间：直播是此刻，回看是播放点
     * @return 没在播放（缓冲中、出错）时不暂停，返回 false
     */
    boolean pause(long pausedAtMs) {
        if (!mVideo.isPlaying()) {
            return false;
        }
        mVideo.pause();
        mPausedAtMs = pausedAtMs;
        mHud.setPaused(true);
        return true;
    }

    /** 清除暂停状态；不操作播放器（调用方接着会换源或恢复）。 */
    void clearPause() {
        mPausedAtMs = 0;
        mHud.setPaused(false);
    }

    /** Activity 回到前台：暂停中保持暂停（HUD 仍显示「已暂停」），否则恢复播放。 */
    void onHostResume() {
        if (!isPaused()) {
            mVideo.resume();
        }
    }

    /** Activity 进入后台：停掉缓冲刷新并暂停播放器。 */
    void onHostPause() {
        stopBuffering();
        mVideo.pause();
    }

    // ---------------- 换台卡片 ----------------

    void showZapCard(int number, Channel channel, int sourceIndex) {
        mZapCard.show(number, channel, sourceIndex,
                mEpg.getCurrentProgram(channel), mEpg.getNextProgramInfo(channel));
    }

    /** 节目单晚于换台到达时补上卡片里的节目信息；卡片不在时什么也不做。 */
    void refreshZapCard(int number, Channel channel, int sourceIndex) {
        if (mZapCard.isShowing()) {
            mZapCard.bind(number, channel, sourceIndex,
                    mEpg.getCurrentProgram(channel), mEpg.getNextProgramInfo(channel));
        }
    }

    void hideZapCard() {
        mZapCard.hide();
    }

    boolean isZapCardShowing() {
        return mZapCard.isShowing();
    }

    // ---------------- 缓冲网速 ----------------

    /** 开始（或重新开始）每秒刷新「缓冲中 · 网速」。 */
    void startBuffering() {
        mHandler.removeCallbacks(mBufferingTick);
        tickBuffering();
    }

    /** 停止刷新并收起提示。 */
    void stopBuffering() {
        mHandler.removeCallbacks(mBufferingTick);
        mStatus.hideMessage();
    }

    private void tickBuffering() {
        mStatus.showMessage(mContext.getString(R.string.buffering_speed,
                PlaybackStatusView.formatSpeed(mVideo.getTcpSpeed())));
        mHandler.postDelayed(mBufferingTick, BUFFERING_TICK_MS);
    }
}
