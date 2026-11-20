package com.cabletv.player.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * 影院暗色界面共用的小工具：dp 换算、统一的文字样式、取色、日期格式。
 *
 * <p>颜色只在 colors.xml 的 cine_* 定义一处，代码里通过 {@link #color} 取，避免两份拷贝不同步。
 */
final class CineViews {

    private CineViews() {
    }

    static int dp(Context context, float value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    /** 无字体上下留白、数字等宽（时间跳动时不晃）的文字。 */
    static TextView text(Context context, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTextColor(color(context, colorRes));
        view.setIncludeFontPadding(false);
        view.setFontFeatureSettings("tnum");
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    /** 日期：中文「11月20日 周五」，其他语言「Fri, Nov 20」。 */
    static SimpleDateFormat dateFormat() {
        boolean zh = Locale.getDefault().getLanguage().equals("zh");
        return new SimpleDateFormat(zh ? "M月d日 EEE" : "EEE, MMM d", Locale.getDefault());
    }
}
