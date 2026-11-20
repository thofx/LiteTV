package com.cabletv.player.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.cabletv.player.R;

/**
 * 遥控器数字选台：左上角玻璃卡片，大号数字 + 将要切到的台名（与换台卡片同一位置、同一风格）。
 */
public class ChannelNumberView extends LinearLayout {
    private final TextView mDigitsView;
    private final TextView mNameView;

    public ChannelNumberView(@NonNull Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_glass_card);
        setPadding(dp2px(20), dp2px(14), dp2px(24), dp2px(14));
        setVisibility(GONE);

        mDigitsView = new TextView(context);
        mDigitsView.setTextColor(CineViews.color(context, R.color.cine_text_1));
        mDigitsView.setTextSize(30);
        mDigitsView.setTypeface(Typeface.DEFAULT_BOLD);
        mDigitsView.setIncludeFontPadding(false);
        mDigitsView.setFontFeatureSettings("tnum");
        addView(mDigitsView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mNameView = new TextView(context);
        mNameView.setTextSize(17);
        mNameView.setTypeface(Typeface.DEFAULT_BOLD);
        mNameView.setMaxLines(1);
        mNameView.setEllipsize(TextUtils.TruncateAt.END);
        mNameView.setMaxWidth(dp2px(280));
        LayoutParams nameLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nameLp.leftMargin = dp2px(16);
        addView(mNameView, nameLp);
    }

    /** 左上角，与换台卡片对齐。 */
    public FrameLayout.LayoutParams createTopStartLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.topMargin = dp2px(28);
        lp.leftMargin = dp2px(48);
        return lp;
    }

    /**
     * @param digits      what the user has typed so far
     * @param channelName the channel that number resolves to, or null when it resolves to none
     */
    public void showDigits(String digits, String channelName) {
        mDigitsView.setText(digits);
        if (channelName == null || channelName.isEmpty()) {
            mNameView.setText(getContext().getString(R.string.channel_number_unknown));
            mNameView.setTextColor(CineViews.color(getContext(), R.color.cine_text_3));
        } else {
            mNameView.setText(channelName);
            mNameView.setTextColor(CineViews.color(getContext(), R.color.cine_text_1));
        }
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void hide() {
        setVisibility(GONE);
    }

    private int dp2px(int dp) {
        return CineViews.dp(getContext(), dp);
    }
}
