package live.teekamsuthar.mutify;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Applies the theme setting (System / Light / Dark) app-wide: at start, before any screen is
 * drawn, and again whenever the setting changes. AppCompat recreates open screens in the new mode.
 */
public class MutifyApp extends Application {

    private MutifySettings settings;
    // a field, so the settings keep a strong reference for as long as the app runs
    private final MutifySettings.Listener themeListener = key -> {
        if (MutifySettings.KEY_THEME_MODE.equals(key)) {
            applyTheme();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        settings = MutifySettings.get(this);
        settings.migrateLegacyDarkOnly();
        applyTheme();
        settings.addListener(themeListener);
    }

    private void applyTheme() {
        int nightMode = settings.getThemeMode().nightMode();
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }
}
