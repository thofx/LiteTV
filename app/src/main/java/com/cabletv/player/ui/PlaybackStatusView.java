package com.cabletv.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.cabletv.player.R;

/**
 * Centred one-line message shown over the video: buffering, reconnect progress, or a
 * playback failure. Without it a dead stream just looks like a black screen.
 */
public class PlaybackStatusView extends TextView {

    public PlaybackStatusView(@NonNull Context context) {
        super(context);
        setBackgroundResource(R.drawable.bg_playback_status);
        setTextColor(0xFFF5F5F7);
        setTextSize(16);
        setGravity(Gravity.CENTER);
        setLineSpacing(0, 1.2f);
        setFontFeatureSettings("tnum");
        setPadding(dp2px(24), dp2px(14), dp2px(24), dp2px(14));
        setVisibility(GONE);
    }

    /** Layout params for adding this view to a FrameLayout, centred on screen. */
    public FrameLayout.LayoutParams createCenteredLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    public void showMessage(CharSequence message) {
        setText(message);
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void hideMessage() {
        setText("");
        setVisibility(GONE);
    }

    /** 把 bytes/s 格式化成 KB/s 或 MB/s。 */
    static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024 * 1024) {
            return Math.max(0, bytesPerSecond / 1024) + " KB/s";
        }
        return String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / (1024f * 1024f));
    }

    private int dp2px(int dp) {
        return Math.round(getContext().getResources().getDisplayMetrics().density * dp);
    }
}
