package com.cabletv.player.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cabletv.player.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The unified playback & time-shift (时移/回看) HUD shown over live playback and catchup.
 *
 * <p>Combines channel information, channel logo, current programme title, scrubbable single-programme timeline,
 * start/end time markers, fixed behind-live difference indicator, seek rate, and the live edge shortcut.
 */
public class TimeShiftBarComponent extends FrameLayout {

    public static final int BAR_HEIGHT_DP = 260;

    private final TextView mChannelNumView;
    private final ChannelLogoView mLogoView;
    private final TextView mChannelLabel;
    private final TextView mSourceBadge;
    private final TextView mLiveBadge;
    private final TextView mBackToLive;

    private final TextView mProgramTitle;
    private final TextView mNextProgramView;

    private final ProgramTimelineView mTimeline;

    private final TextView mStartTimeLabel;
    private final TextView mEndTimeLabel;
    private final TextView mTargetLabel;
    private final TextView mRateLabel;
    private final TextView mBehindLiveLabel;
    private final TextView mRangeLabel;
    private final LinearLayout mHintsRow;
    private boolean mPaused;
    private boolean mCanSeek;
    /** 是否有节目单：没有时不显示时段和剩余时间。 */
    private boolean mHasProgram;

    private OnSeekListener mSeekListener;
    private static final long AUTO_HIDE_MS = 6_000;
    private final Runnable mAutoHideRunnable = this::onAutoHide;
    private boolean mAutoHidePending;
    private boolean mRateVisible;

    private long mWindowStartMs = 0;
    private long mWindowEndMs = 0;
    private long mPositionMs = 0;
    private long mLivePointMs = 0;
    private long mFixedBehindMs = 0;
    private boolean mLiveMode = true;
    private boolean mDragging = false;

    public interface OnSeekListener {
        /** The user released the thumb at this absolute epoch time; seek the playhead there. */
        void onSeekTo(long targetMs);

        /** The user asked to jump straight to the live edge (返回直播). */
        void onBackToLive();
    }

    public TimeShiftBarComponent(@NonNull Context context) {
        super(context);
        applyBackground();
        // 上方留出渐变过渡区，内容压在画面底部
        setPadding(dp2px(48), dp2px(72), dp2px(48), dp2px(24));
        setVisibility(GONE);
        setClickable(true);

        LinearLayout rootContainer = new LinearLayout(context);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setGravity(Gravity.BOTTOM);
        rootContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(rootContainer);

        // ================= 第一行：台号 · 台标 · 台名 · 状态 =================
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        rootContainer.addView(row1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp2px(40)));

        mChannelNumView = CineViews.text(context, 15, R.color.cine_text_2, true);
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        numLp.rightMargin = dp2px(12);
        row1.addView(mChannelNumView, numLp);

        // 台标块：没有台标时显示由台名生成的文字块，尺寸不变
        mLogoView = new ChannelLogoView(context, 8, 13);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp2px(56), dp2px(36));
        logoLp.rightMargin = dp2px(14);
        row1.addView(mLogoView, logoLp);

        mChannelLabel = CineViews.text(context, 26, R.color.cine_text_1, true);
        mChannelLabel.setMaxLines(1);
        mChannelLabel.setEllipsize(TextUtils.TruncateAt.END);
        row1.addView(mChannelLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mLiveBadge = badge(context, CineViews.color(context, R.color.cine_accent));
        mLiveBadge.setText(R.string.hud_live);
        row1.addView(mLiveBadge, badgeLp());

        // 回看 / 暂停状态胶囊；触屏点它直接回直播
        mBackToLive = badge(context, 0x29FFFFFF);
        mBackToLive.setVisibility(GONE);
        mBackToLive.setOnClickListener(v -> {
            if (mSeekListener != null) {
                mSeekListener.onBackToLive();
            }
        });
        row1.addView(mBackToLive, badgeLp());

        mSourceBadge = CineViews.text(context, 13, R.color.cine_text_2, false);
        mSourceBadge.setVisibility(GONE);
        LinearLayout.LayoutParams srcLp = badgeLp();
        srcLp.leftMargin = dp2px(14);
        row1.addView(mSourceBadge, srcLp);

        // ================= 第二行：当前节目 + 时段 =================
        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r2Lp.topMargin = dp2px(10);
        rootContainer.addView(row2, r2Lp);

        mProgramTitle = CineViews.text(context, 18, R.color.cine_text_1, false);
        mProgramTitle.setMaxLines(1);
        mProgramTitle.setEllipsize(TextUtils.TruncateAt.END);
        row2.addView(mProgramTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mRangeLabel = CineViews.text(context, 13, R.color.cine_text_2, false);
        LinearLayout.LayoutParams rangeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rangeLp.leftMargin = dp2px(12);
        rangeLp.bottomMargin = dp2px(2);
        row2.addView(mRangeLabel, rangeLp);

        // ================= 第三行：节目进度条 =================
        mTimeline = new ProgramTimelineView(context);
        mTimeline.setOnScrubListener(new ProgramTimelineView.OnScrubListener() {
            @Override
            public void onScrubStart() {
                mDragging = true;
                cancelAutoHide();
            }

            @Override
            public void onScrubTo(long timeMs) {
                mPositionMs = timeMs;
                if (!mLiveMode) {
                    long now = mLivePointMs > 0 ? mLivePointMs : System.currentTimeMillis();
                    mFixedBehindMs = Math.max(0, now - mPositionMs);
                }
                updateLabels();
            }

            @Override
            public void onScrubCommit(long timeMs) {
                mDragging = false;
                commitPosition(timeMs);
                scheduleAutoHide();
            }
        });
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp2px(22));
        tlLp.topMargin = dp2px(10);
        rootContainer.addView(mTimeline, tlLp);

        // ================= 第四行：起止时间 · 当前位置 / 倍速 =================
        FrameLayout row4 = new FrameLayout(context);
        // 与时间轴轨道两端对齐（轨道内缩了圆点半径）
        row4.setPadding(dp2px(ProgramTimelineView.EDGE_PAD_DP), 0,
                dp2px(ProgramTimelineView.EDGE_PAD_DP), 0);
        rootContainer.addView(row4, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp2px(20)));

        mStartTimeLabel = CineViews.text(context, 12, R.color.cine_text_2, false);
        row4.addView(mStartTimeLabel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout centerGroup = new LinearLayout(context);
        centerGroup.setOrientation(LinearLayout.HORIZONTAL);
        centerGroup.setGravity(Gravity.CENTER);
        row4.addView(centerGroup, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        mTargetLabel = CineViews.text(context, 12, R.color.cine_text_1, true);
        mTargetLabel.setPadding(0, 0, dp2px(8), 0);
        centerGroup.addView(mTargetLabel);

        // 慢于直播的时长现在显示在第一行的状态胶囊里，这里保留占位以兼容测试
        mBehindLiveLabel = CineViews.text(context, 12, R.color.cine_text_1, true);
        mBehindLiveLabel.setVisibility(GONE);
        centerGroup.addView(mBehindLiveLabel);

        mRateLabel = CineViews.text(context, 12, R.color.cine_text_1, true);
        mRateLabel.setVisibility(GONE);
        centerGroup.addView(mRateLabel);

        mEndTimeLabel = CineViews.text(context, 12, R.color.cine_text_2, false);
        row4.addView(mEndTimeLabel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));

        // ================= 第五行：下一个节目 · 按键提示 =================
        LinearLayout row5 = new LinearLayout(context);
        row5.setOrientation(LinearLayout.HORIZONTAL);
        row5.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams r5Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r5Lp.topMargin = dp2px(10);
        rootContainer.addView(row5, r5Lp);

        mNextProgramView = CineViews.text(context, 13, R.color.cine_text_2, false);
        mNextProgramView.setMaxLines(1);
        mNextProgramView.setEllipsize(TextUtils.TruncateAt.END);
        row5.addView(mNextProgramView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        mHintsRow = new LinearLayout(context);
        mHintsRow.setOrientation(LinearLayout.HORIZONTAL);
        mHintsRow.setGravity(Gravity.CENTER_VERTICAL);
        row5.addView(mHintsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        renderHints();
    }

    private TextView badge(Context context, int bg) {
        TextView tv = CineViews.text(context, 12, R.color.cine_text_1, true);
        tv.setBackground(pill(bg, 6));
        tv.setPadding(dp2px(10), dp2px(4), dp2px(10), dp2px(4));
        return tv;
    }

    private LinearLayout.LayoutParams badgeLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp2px(16);
        return lp;
    }

    private GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp2px(radiusDp));
        return gd;
    }

    /** 状态变化时重建按键提示：只列出当前真正可用的操作。 */
    private void renderHints() {
        mHintsRow.removeAllViews();
        boolean replay = !mLiveMode;
        addHint(R.string.hint_key_ok, mPaused ? R.string.hint_resume : R.string.hint_pause);
        if (mCanSeek) {
            addHint(R.string.hint_key_lr, R.string.hint_seek);
        }
        addHint(R.string.hint_key_back,
                (replay || mPaused) ? R.string.hint_back_live : R.string.hint_hide);
    }

    private void addHint(int keyRes, int labelRes) {
        Context context = getContext();
        TextView key = CineViews.text(context, 12, R.color.cine_text_1, true);
        key.setText(keyRes);
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(dp2px(5));
        border.setStroke(dp2px(1), 0x66FFFFFF);
        key.setBackground(border);
        key.setPadding(dp2px(7), dp2px(3), dp2px(7), dp2px(3));
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        keyLp.leftMargin = dp2px(mHintsRow.getChildCount() == 0 ? 0 : 20);
        mHintsRow.addView(key, keyLp);
        TextView label = CineViews.text(context, 12, R.color.cine_text_2, false);
        label.setText(labelRes);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = dp2px(6);
        mHintsRow.addView(label, labelLp);
    }

    /**
     * 暂停状态：HUD 常驻不自动隐藏，状态胶囊显示「已暂停」。
     */
    public void setPaused(boolean paused) {
        if (mPaused == paused) {
            return;
        }
        mPaused = paused;
        if (paused) {
            cancelAutoHide();
        } else if (getVisibility() == VISIBLE) {
            scheduleAutoHide();
        }
        updateBehindLive();
        renderHints();
    }

    /** 频道是否支持回看：不支持时不提示左右键。 */
    public void setCanSeek(boolean canSeek) {
        if (mCanSeek == canSeek) {
            return;
        }
        mCanSeek = canSeek;
        renderHints();
    }

    public void setChannelInfo(String name, int channelNumber, int urlIndex, int urlCount) {
        setChannelInfo(name, channelNumber, urlIndex, urlCount, null);
    }

    public void setChannelInfo(String name, int channelNumber, int urlIndex, int urlCount, @Nullable String logoUrl) {
        if (mChannelLabel != null) {
            mChannelLabel.setText(name);
        }
        if (mChannelNumView != null) {
            if (channelNumber > 0) {
                mChannelNumView.setText(String.format(Locale.getDefault(), "%03d", channelNumber));
                mChannelNumView.setVisibility(VISIBLE);
            } else {
                mChannelNumView.setVisibility(GONE);
            }
        }
        if (mSourceBadge != null) {
            if (urlCount > 1) {
                mSourceBadge.setText(getContext().getString(R.string.zap_source, urlIndex, urlCount));
                mSourceBadge.setVisibility(VISIBLE);
            } else {
                mSourceBadge.setVisibility(GONE);
            }
        }
        mLogoView.bind(name, logoUrl);
    }

    public void setProgramInfo(String currentProgram, String nextProgram) {
        if (mProgramTitle != null) {
            mHasProgram = currentProgram != null && !currentProgram.isEmpty();
            if (mHasProgram) {
                mProgramTitle.setText(currentProgram);
            } else {
                mProgramTitle.setText(R.string.no_epg_data);
            }
        }
        if (mNextProgramView != null) {
            if (nextProgram != null && !nextProgram.isEmpty()) {
                mNextProgramView.setText(getContext().getString(R.string.hud_next, nextProgram));
                mNextProgramView.setVisibility(VISIBLE);
            } else {
                mNextProgramView.setText("");
                mNextProgramView.setVisibility(GONE);
            }
        }
    }

    public void showWhileSeeking() {
        cancelAutoHide();
        clearAnimation();
        setAlpha(1f);
        setVisibility(VISIBLE);
    }

    public void endSeeking() {
        hideSeekRate();
        scheduleAutoHide();
    }

    public void showSeekRate(int rate) {
        if (mRateLabel == null) {
            return;
        }
        mRateVisible = true;
        mBehindLiveLabel.setVisibility(GONE);
        mRateLabel.setText(rate + "x");
        mRateLabel.setVisibility(VISIBLE);
    }

    public void showSeekPreview(int rate, long deltaMs) {
        if (mRateLabel == null) {
            return;
        }
        mRateVisible = true;
        mBehindLiveLabel.setVisibility(GONE);
        long seconds = Math.abs(deltaMs) / 1000;
        mRateLabel.setText(rate + "x · " + (deltaMs < 0 ? "-" : "+") + seconds + "s");
        mRateLabel.setVisibility(VISIBLE);
    }

    public void hideSeekRate() {
        mRateVisible = false;
        if (mRateLabel != null) {
            mRateLabel.setVisibility(GONE);
        }
        updateBehindLive();
    }

    ProgramTimelineView findTimeline() {
        return mTimeline;
    }

    String findShownPositionLabel() {
        return mTargetLabel.getText().toString();
    }

    long findShownPositionMs() {
        return mPositionMs;
    }

    long findBehindLiveMs() {
        if (mFixedBehindMs > 0) {
            return mFixedBehindMs;
        }
        return mLivePointMs > 0 ? mLivePointMs - mPositionMs : 0;
    }

    boolean hasPendingAutoHide() {
        return mAutoHidePending;
    }

    public FrameLayout.LayoutParams createBottomLayoutParams() {
        // 整宽贴底，背景是自下而上的渐变，不再是悬浮圆角框
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp2px(BAR_HEIGHT_DP));
        lp.gravity = Gravity.BOTTOM;
        return lp;
    }

    private static final int INFO_BAR_HEIGHT_DP = 100;
    private static final int COMPACT_MARGIN_DP = 8;
    private static final int MIN_GAP_DP = 24;

    static int bottomMarginDp(int screenHeightDp) {
        int needed = 88 + INFO_BAR_HEIGHT_DP + MIN_GAP_DP;
        return screenHeightDp >= needed ? INFO_BAR_HEIGHT_DP : COMPACT_MARGIN_DP;
    }

    public void setOnSeekListener(@Nullable OnSeekListener listener) {
        mSeekListener = listener;
    }

    public void setWindow(long windowStartMs, long windowEndMs, long positionMs) {
        mWindowStartMs = windowStartMs;
        mWindowEndMs = Math.max(windowStartMs + 1000, windowEndMs);
        mPositionMs = Math.max(windowStartMs, Math.min(mWindowEndMs, positionMs));
        mTimeline.setWindow(mWindowStartMs, mWindowEndMs, mPositionMs);
        if (!mLiveMode) {
            long now = mLivePointMs > 0 ? mLivePointMs : System.currentTimeMillis();
            mFixedBehindMs = Math.max(0, now - mPositionMs);
        } else {
            mFixedBehindMs = 0;
        }
        updateLabels();
    }

    public long getWindowStart() {
        return mWindowStartMs;
    }

    public long getWindowEnd() {
        return mWindowEndMs;
    }

    public boolean isPastLastProgramme() {
        return mTimeline.isPastLastSegment();
    }

    public void setPrograms(List<ProgramTimelineView.Segment> segments) {
        mTimeline.setSegments(segments);
        updateProgramTitle();
    }

    public void setLiveEdgeVisible(boolean visible) {
        mTimeline.setLiveEdgeVisible(visible);
    }

    private void updateProgramTitle() {
        String title = null;
        for (ProgramTimelineView.Segment segment : mTimeline.getSegments()) {
            if (mPositionMs >= segment.startTime && mPositionMs <= segment.endTime) {
                title = segment.title;
                break;
            }
        }
        if (title != null && !title.isEmpty()) {
            mProgramTitle.setText(title);
        }
    }

    static String formatBehind(long behindMs) {
        if (behindMs <= 0) {
            return "0:00";
        }
        long total = (behindMs + 500) / 1000;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    void commitPosition(long timeMs) {
        mPositionMs = Math.max(mWindowStartMs, Math.min(mWindowEndMs, timeMs));
        if (!mLiveMode) {
            long now = mLivePointMs > 0 ? mLivePointMs : System.currentTimeMillis();
            mFixedBehindMs = Math.max(0, now - mPositionMs);
        }
        updateLabels();
        if (mSeekListener != null) {
            mSeekListener.onSeekTo(mPositionMs);
        }
    }

    public void setChannelLabel(String label) {
        mChannelLabel.setText(label);
    }

    public void setBackToLiveVisible(boolean visible) {
        updateBehindLive();
    }

    private void updateLabels() {
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        mTargetLabel.setText(timeFmt.format(new Date(mPositionMs)));
        mStartTimeLabel.setText(timeFmt.format(new Date(mWindowStartMs)));
        mEndTimeLabel.setText(timeFmt.format(new Date(mWindowEndMs)));
        mTimeline.setPosition(mPositionMs);
        if (mHasProgram) {
            long remainingMin = Math.max(0, (mWindowEndMs - mPositionMs + 59_999) / 60_000);
            mRangeLabel.setText(getContext().getString(R.string.hud_time_range,
                    timeFmt.format(new Date(mWindowStartMs)), timeFmt.format(new Date(mWindowEndMs)),
                    (int) remainingMin));
            mRangeLabel.setVisibility(VISIBLE);
        } else {
            mRangeLabel.setVisibility(GONE);
        }
        updateBehindLive();
    }

    /**
     * 第一行的状态：直播中显示红色「直播」；回看中显示「回看中 · 慢于直播 x」；暂停时显示「已暂停」。
     */
    private void updateBehindLive() {
        mBehindLiveLabel.setVisibility(GONE);
        long behind = mFixedBehindMs;
        if (behind <= 0 && !mLiveMode) {
            long now = mLivePointMs > 0 ? mLivePointMs : System.currentTimeMillis();
            behind = Math.max(0, now - mPositionMs);
        }
        if (!mLiveMode) {
            mBackToLive.setText(getContext().getString(R.string.hud_catchup, formatBehind(behind)));
        } else if (mPaused) {
            mBackToLive.setText(R.string.hud_paused);
        }
        boolean showStatus = !mLiveMode || mPaused;
        mBackToLive.setVisibility(showStatus ? VISIBLE : GONE);
        mLiveBadge.setVisibility(showStatus ? GONE : VISIBLE);
    }

    public void setPlaybackPosition(long positionMs) {
        if (mDragging) {
            return;
        }
        long clamped = Math.max(mWindowStartMs, Math.min(mWindowEndMs, positionMs));
        if (clamped == mPositionMs) {
            return;
        }
        mPositionMs = clamped;
        mTimeline.setPosition(mPositionMs);
        updateLabels();
    }

    public void tickIfIdle() {
        tickIfIdle(System.currentTimeMillis());
    }

    void tickIfIdle(long now) {
        if (mDragging || mPaused || getVisibility() != VISIBLE) {
            // 暂停中画面不动，进度也不动
            return;
        }
        mLivePointMs = now;
        if (mLiveMode) {
            mWindowEndMs = Math.max(mWindowEndMs, now);
            mPositionMs = now;
            mTimeline.setWindow(mWindowStartMs, mWindowEndMs, mPositionMs);
        }
        updateLabels();
    }

    public void setLiveMode(boolean liveMode, long livePointMs) {
        mLiveMode = liveMode;
        mLivePointMs = livePointMs;
        if (!liveMode) {
            mFixedBehindMs = Math.max(0, livePointMs - mPositionMs);
        } else {
            mFixedBehindMs = 0;
        }
        mTimeline.setLiveEdgeVisible(liveMode);
        updateBehindLive();
        renderHints();
    }

    /** 底部渐变：最深处约 94% 黑，向上淡出，亮画面上文字也清楚。 */
    private void applyBackground() {
        int bottom = 0xF0000000;
        int mid = 0xB4000000;
        setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] { bottom, bottom, mid, 0x00000000 }));
    }

    public void show() {
        removeCallbacks(mAutoHideRunnable);
        clearAnimation();
        setAlpha(1f);
        setVisibility(VISIBLE);
        scheduleAutoHide();
    }

    public void hideSmooth() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        AlphaAnimation fade = new AlphaAnimation(1.0f, 0.0f);
        fade.setDuration(300);
        fade.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) {
                setVisibility(GONE);
            }
        });
        startAnimation(fade);
    }

    public void hide() {
        removeCallbacks(mAutoHideRunnable);
        setVisibility(GONE);
    }

    private void scheduleAutoHide() {
        cancelAutoHide();
        if (mPaused) {
            return;
        }
        mAutoHidePending = true;
        postDelayed(mAutoHideRunnable, AUTO_HIDE_MS);
    }

    private void cancelAutoHide() {
        mAutoHidePending = false;
        removeCallbacks(mAutoHideRunnable);
    }

    private void onAutoHide() {
        mAutoHidePending = false;
        hideSmooth();
    }

    private int dp2px(int dp) {
        return Math.round(getContext().getResources().getDisplayMetrics().density * dp);
    }
}
