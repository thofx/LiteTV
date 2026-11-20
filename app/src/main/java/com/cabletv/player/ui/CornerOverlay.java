package com.cabletv.player.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.cabletv.player.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 屏幕右上角的常驻信息：时钟，以及其下方的「回看中 · 慢于直播 0:42」。
 *
 * <p>放右上角而不是左上角：国内频道的台标几乎都在左上角。
 *
 * <p>自己每秒刷新一次，显示与否问 {@link Host}，宿主不用在每个弹层开关处同步状态。
 */
public class CornerOverlay extends FrameLayout {

    public interface Host {
        /** 是否显示时钟（有浮层打开，或设置为常显）。 */
        boolean wantsClock();

        /** 回看中慢于直播多少毫秒；不在回看中返回 -1。 */
        long behindLiveMs();
    }

    private static final long TICK_MS = 1000;

    private final TextView mClock;
    private final TextView mDate;
    private final LinearLayout mClockBox;
    /** 时钟显示时顶部的淡渐变，亮画面上也看得清。 */
    private final android.view.View mTopScrim;
    private final TextView mReplayPill;
    private final Host mHost;
    private final SimpleDateFormat mTimeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat mDateFmt;
    private final Runnable mTick = this::tick;

    public CornerOverlay(@NonNull Context context, @NonNull Host host) {
        super(context);
        mHost = host;
        setClickable(false);
        setFocusable(false);
        mDateFmt = CineViews.dateFormat();

        mTopScrim = new android.view.View(context);
        mTopScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x80000000, 0x33000000, 0x00000000 }));
        mTopScrim.setVisibility(GONE);
        addView(mTopScrim, new LayoutParams(LayoutParams.MATCH_PARENT, dp(150), Gravity.TOP));

        mClockBox = new LinearLayout(context);
        mClockBox.setOrientation(LinearLayout.VERTICAL);
        mClockBox.setGravity(Gravity.END);
        mClock = CineViews.text(context, 26, R.color.cine_text_1, true);
        mDate = CineViews.text(context, 11, R.color.cine_text_1, false);
        mClockBox.addView(mClock);
        mClockBox.addView(mDate);
        // 文字阴影：亮画面上也看得清
        mClock.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        mDate.setShadowLayer(dp(3), 0, dp(1), 0xB3000000);
        LayoutParams clockLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clockLp.setMargins(0, dp(28), dp(48), 0);
        addView(mClockBox, clockLp);

        mReplayPill = CineViews.text(context, 13, R.color.cine_text_1, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CineViews.color(context, R.color.cine_card));
        bg.setCornerRadius(dp(8));
        mReplayPill.setBackground(bg);
        mReplayPill.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pillLp.topMargin = dp(10);
        mClockBox.addView(mReplayPill, pillLp);
        mReplayPill.setVisibility(GONE);
        mClock.setVisibility(GONE);
        mDate.setVisibility(GONE);
    }

    /** 状态变化后立即刷新一次，不必等下一秒。 */
    public void refresh() {
        removeCallbacks(mTick);
        tick();
    }

    private void tick() {
        Date now = new Date();
        boolean clock = mHost.wantsClock();
        mClock.setVisibility(clock ? VISIBLE : GONE);
        mTopScrim.setVisibility(clock ? VISIBLE : GONE);
        mDate.setVisibility(clock ? VISIBLE : GONE);
        if (clock) {
            mClock.setText(mTimeFmt.format(now));
            mDate.setText(mDateFmt.format(now));
        }
        long behind = mHost.behindLiveMs();
        if (behind >= 0) {
            mReplayPill.setText(getContext().getString(R.string.hud_catchup,
                    TimeShiftBarComponent.formatBehind(behind)));
            mReplayPill.setVisibility(VISIBLE);
        } else {
            mReplayPill.setVisibility(GONE);
        }
        postDelayed(mTick, TICK_MS);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mTick);
        super.onDetachedFromWindow();
    }

    private int dp(int v) {
        return CineViews.dp(getContext(), v);
    }
}
