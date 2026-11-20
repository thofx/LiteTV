package com.cabletv.player.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cabletv.player.R;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * 频道台标块：有台标显示台标（浅底），没有台标或加载失败时显示由台名生成的文字块（深灰底）。
 *
 * <p>尺寸固定，所以频道列表里有无台标的行都能对齐。HUD、换台卡片、频道列表共用。
 */
public class ChannelLogoView extends FrameLayout {


    private final ImageView mImage;
    private final TextView mMonogram;
    private final GradientDrawable mBg = new GradientDrawable();
    /** 当前绑定的台标地址，用来丢弃过期的异步加载结果。 */
    private String mBoundUrl;

    public ChannelLogoView(@NonNull Context context, int radiusDp, int textSp) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        mBg.setCornerRadius(radiusDp * density);
        setBackground(mBg);
        int pad = Math.round(4 * density);

        mImage = new ImageView(context);
        mImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mImage.setPadding(pad, pad, pad, pad);
        addView(mImage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mMonogram = new TextView(context);
        mMonogram.setGravity(Gravity.CENTER);
        mMonogram.setTextColor(CineViews.color(context, R.color.cine_monogram_text));
        mMonogram.setTextSize(textSp);
        mMonogram.setTypeface(Typeface.DEFAULT_BOLD);
        mMonogram.setMaxLines(1);
        mMonogram.setIncludeFontPadding(false);
        addView(mMonogram, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** 绑定频道：logoUrl 为空或加载失败时退回文字块。 */
    public void bind(@Nullable String name, @Nullable String logoUrl) {
        String url = logoUrl == null ? "" : logoUrl.trim();
        mMonogram.setText(monogram(name));
        if (url.isEmpty()) {
            mBoundUrl = null;
            showMonogram();
            return;
        }
        if (url.equals(mBoundUrl) && mImage.getDrawable() != null) {
            return;
        }
        mBoundUrl = url;
        // 先显示文字块，台标到了再替换，弱网下也不会出现空框
        showMonogram();
        try {
            Glide.with(getContext())
                    .load(url)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                Target<Drawable> target, boolean isFirstResource) {
                            post(() -> {
                                if (url.equals(mBoundUrl)) {
                                    showMonogram();
                                }
                            });
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                Target<Drawable> target, DataSource dataSource,
                                boolean isFirstResource) {
                            if (url.equals(mBoundUrl)) {
                                showImage();
                            }
                            return false;
                        }
                    })
                    .into(mImage);
        } catch (Exception e) {
            // Activity 已销毁等情况下 Glide 会抛异常，保持文字块即可
            showMonogram();
        }
    }

    private void showMonogram() {
        mBg.setColor(CineViews.color(getContext(), R.color.cine_monogram_bg));
        // INVISIBLE 而不是 GONE：Glide 需要视图尺寸才会开始加载
        mImage.setVisibility(INVISIBLE);
        mMonogram.setVisibility(VISIBLE);
    }

    private void showImage() {
        mBg.setColor(CineViews.color(getContext(), R.color.cine_focus));
        mMonogram.setVisibility(GONE);
        mImage.setVisibility(VISIBLE);
    }

    /**
     * 由台名生成文字块内容：字母数字开头取前面一段（最多 5 个字符，如 CCTV-1综合 → CCTV1），
     * 否则取前两个字（湖南卫视 → 湖南）。
     */
    static String monogram(@Nullable String name) {
        if (name == null) {
            return "";
        }
        String compact = name.replaceAll("[\\s\\-_·.+]", "");
        if (compact.isEmpty()) {
            return "";
        }
        if (isAsciiAlnum(compact.charAt(0))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < compact.length() && sb.length() < 5; i++) {
                char c = compact.charAt(i);
                if (!isAsciiAlnum(c)) {
                    break;
                }
                sb.append(Character.toUpperCase(c));
            }
            return sb.toString();
        }
        int end = compact.offsetByCodePoints(0, Math.min(2, compact.codePointCount(0, compact.length())));
        return compact.substring(0, end);
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
