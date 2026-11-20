package com.cabletv.player.ui;

import android.content.Context;
import android.content.res.Configuration;

import com.cabletv.player.config.AppConfig;

import java.util.Locale;

/**
 * 应用内语言切换：按设置把 Activity 的 Context 换成指定语言；「跟随系统」时原样返回。
 */
final class LocaleHelper {

    /** 进程启动时的系统语言，切回「跟随系统」时用它恢复默认 Locale。 */
    private static final Locale SYSTEM_DEFAULT = Locale.getDefault();

    private LocaleHelper() {
    }

    static Context wrap(Context base) {
        Locale locale = localeFor(AppConfig.getLanguage());
        // 日期格式等直接读 Locale.getDefault()，一并切换
        Locale.setDefault(locale != null ? locale : SYSTEM_DEFAULT);
        if (locale == null) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }

    /** 简体中文 / English；跟随系统时返回 null。 */
    static Locale localeFor(String language) {
        if (AppConfig.LANGUAGE_ZH.equals(language)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if (AppConfig.LANGUAGE_EN.equals(language)) {
            return Locale.ENGLISH;
        }
        return null;
    }
}
