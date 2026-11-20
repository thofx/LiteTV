package com.cabletv.player.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cabletv.player.R;
import com.cabletv.player.epg.EpgManager;
import com.cabletv.player.model.Channel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 换台时左上角弹出的轻量卡片：台号、台标、台名、当前节目与进度，几秒后自动消失。
 *
 * <p>换台不再弹出底部 HUD，这样 HUD 不在时左右键始终是「频道列表 / 设置」。
 */
public class ZapCardView extends LinearLayout {

    private static final long AUTO_HIDE_MS = 4_000;

    private final TextView mNumber;
    private final ChannelLogoView mLogo;
    private final TextView mName;
    private final TextView mSource;
    private final TextView mProgram;
    private final TextView mRange;
    private final ProgressBar mProgress;
    private final TextView mNext;
    private final LinearLayout mProgramBox;
    private final Runnable mHide = () -> animate().alpha(0f).setDuration(250)
            .withEndAction(() -> setVisibility(GONE)).start();
    private final SimpleDateFormat mFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ZapCardView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(18), dp(16), dp(18), dp(16));
        setBackgroundResource(R.drawable.bg_glass_card);
        setVisibility(GONE);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        addView(head);

        mNumber = CineViews.text(context, 30, R.color.cine_text_1, true);
        head.addView(mNumber);

        mLogo = new ChannelLogoView(context, 6, 12);
        LayoutParams logoLp = new LayoutParams(dp(48), dp(31));
        logoLp.leftMargin = dp(12);
        head.addView(mLogo, logoLp);

        LinearLayout nameBox = new LinearLayout(context);
        nameBox.setOrientation(VERTICAL);
        LayoutParams nameLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        nameLp.leftMargin = dp(12);
        head.addView(nameBox, nameLp);
        mName = CineViews.text(context, 19, R.color.cine_text_1, true);
        mName.setMaxLines(1);
        mName.setEllipsize(TextUtils.TruncateAt.END);
        nameBox.addView(mName);
        mSource = CineViews.text(context, 11, R.color.cine_text_2, false);
        nameBox.addView(mSource);

        mProgramBox = new LinearLayout(context);
        mProgramBox.setOrientation(VERTICAL);
        LayoutParams progLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        progLp.topMargin = dp(12);
        addView(mProgramBox, progLp);

        FrameLayout titleRow = new FrameLayout(context);
        mProgramBox.addView(titleRow);
        mProgram = CineViews.text(context, 14, R.color.cine_text_1, false);
        mProgram.setMaxLines(1);
        mProgram.setEllipsize(TextUtils.TruncateAt.END);
        mProgram.setPadding(0, 0, dp(96), 0);
        titleRow.addView(mProgram);
        mRange = CineViews.text(context, 11, R.color.cine_text_2, false);
        titleRow.addView(mRange, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));

        mProgress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        mProgress.setMax(1000);
        mProgress.setProgressDrawable(context.getDrawable(R.drawable.progress_zap));
        LayoutParams barLp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(3));
        barLp.topMargin = dp(8);
        mProgramBox.addView(mProgress, barLp);

        mNext = CineViews.text(context, 11, R.color.cine_text_2, false);
        mNext.setMaxLines(1);
        mNext.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams nextLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        nextLp.topMargin = dp(8);
        mProgramBox.addView(mNext, nextLp);
    }

    public FrameLayout.LayoutParams createLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(360),
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.setMargins(dp(48), dp(28), 0, 0);
        return lp;
    }

    /** 填充内容并显示，几秒后自动隐藏。 */
    public void show(int number, Channel channel, int sourceIndex,
            @Nullable EpgManager.Program current, @Nullable String next) {
        bind(number, channel, sourceIndex, current, next);
        removeCallbacks(mHide);
        animate().cancel();
        setAlpha(1f);
        setVisibility(VISIBLE);
        bringToFront();
        postDelayed(mHide, AUTO_HIDE_MS);
    }

    /**
     * 只更新内容、不重新计时（节目单晚到时用）。current 为空表示没有节目单，只显示频道信息。
     */
    public void bind(int number, Channel channel, int sourceIndex,
            @Nullable EpgManager.Program current, @Nullable String next) {
        mNumber.setText(String.format(Locale.getDefault(), "%03d", number));
        mName.setText(channel.name);
        mLogo.bind(channel.name, channel.logo);
        if (channel.urlCount() > 1) {
            mSource.setText(getContext().getString(R.string.zap_source, sourceIndex, channel.urlCount()));
            mSource.setVisibility(VISIBLE);
        } else {
            mSource.setVisibility(GONE);
        }
        if (current == null || TextUtils.isEmpty(current.title) || current.endTime <= current.startTime) {
            mProgramBox.setVisibility(GONE);
            return;
        }
        mProgramBox.setVisibility(VISIBLE);
        mProgram.setText(current.title);
        mRange.setText(mFmt.format(new Date(current.startTime)) + " – " + mFmt.format(new Date(current.endTime)));
        long now = System.currentTimeMillis();
        long span = current.endTime - current.startTime;
        mProgress.setProgress((int) Math.max(0, Math.min(1000, (now - current.startTime) * 1000 / span)));
        if (!TextUtils.isEmpty(next)) {
            mNext.setText(getContext().getString(R.string.hud_next, next));
            mNext.setVisibility(VISIBLE);
        } else {
            mNext.setVisibility(GONE);
        }
    }

    public void hide() {
        removeCallbacks(mHide);
        animate().cancel();
        setVisibility(GONE);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    private int dp(int value) {
        return CineViews.dp(getContext(), value);
    }
}
