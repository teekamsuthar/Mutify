package live.teekamsuthar.mutify;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mutify's stored settings, read and written by every surface (main screen, tile, widget) and
 * read by {@link SpotifyAdListenerService}. Nothing is started or stopped: switching Mutify on or
 * off only changes {@link #KEY_ENABLED}, and whoever cares listens through {@link Listener}.
 * <p>
 * One instance per process ({@link #get(Context)}). {@link SharedPreferences} keeps its change
 * listeners only weakly, so this class registers itself as the listener: the instance lives as long
 * as the process. A separate listener object held in a field is not enough — R8 removes a field that
 * is only read in the constructor, and the listener was then collected in release builds, so no
 * setting change reached the listener service any more.
 */
public final class MutifySettings implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Called on the main thread after one of the settings below changed. */
    public interface Listener {
        void onSettingChanged(@NonNull String key);
    }

    /** Ad muting on/off. Same key as the fork's earlier builds, so their state carries over. */
    public static final String KEY_ENABLED = "mute_ads_enabled";
    /** What happens to ads: {@link AdVolumeSession#STEP_MUTE}, or 1–5 for 10–50 % of the volume. */
    public static final String KEY_AD_VOLUME_STEP = "ad_volume_step";
    /** The theme: {@link ThemeMode#storedValue()}; follows the system while unset. */
    public static final String KEY_THEME_MODE = "theme_mode";
    /** 2.6.2's "Dark-Only Mode" menu item; only read to carry it over to {@link #KEY_THEME_MODE}. */
    private static final String LEGACY_KEY_DARK_ONLY = "enabled";

    /** The keys this class owns. */
    private static final Set<String> KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            KEY_ENABLED, KEY_AD_VOLUME_STEP, KEY_THEME_MODE)));

    @Nullable
    private static MutifySettings instance;

    private final SharedPreferences prefs;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private MutifySettings(Context context) {
        this(Utils.getPrefs(context.getApplicationContext()));
    }

    /** For tests: the settings on top of the given preferences. */
    MutifySettings(SharedPreferences prefs) {
        this.prefs = prefs;
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public static synchronized MutifySettings get(Context context) {
        if (instance == null) {
            instance = new MutifySettings(context);
        }
        return instance;
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    /** Switches ad muting on or off; listeners hear about it, and the listener service reacts at once. */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** The Adjust Ads Volume step; mute ({@link AdVolumeSession#STEP_MUTE}) unless the user chose otherwise. */
    public int getAdVolumeStep() {
        return clampAdVolumeStep(prefs.getInt(KEY_AD_VOLUME_STEP, AdVolumeSession.STEP_MUTE));
    }

    /**
     * Takes effect at once: an ad that is playing moves to the new step (the slider wins over the
     * volume keys during an ad), and later ads start at it.
     */
    public void setAdVolumeStep(int step) {
        prefs.edit().putInt(KEY_AD_VOLUME_STEP, clampAdVolumeStep(step)).apply();
    }

    private static int clampAdVolumeStep(int step) {
        return Math.max(AdVolumeSession.STEP_MUTE, Math.min(step, AdVolumeSession.MAX_STEP));
    }

    /** The theme setting; 2.6.2's "Dark-Only Mode" counts as Dark until a theme is chosen. */
    @NonNull
    public ThemeMode getThemeMode() {
        return ThemeMode.fromStored(prefs.getString(KEY_THEME_MODE, null),
                prefs.getBoolean(LEGACY_KEY_DARK_ONLY, false));
    }

    /** Stores the theme; MutifyApp hears about it and switches the night mode app-wide. */
    public void setThemeMode(@NonNull ThemeMode mode) {
        prefs.edit()
                .putString(KEY_THEME_MODE, mode.storedValue())
                .remove(LEGACY_KEY_DARK_ONLY)
                .apply();
    }

    /** Moves 2.6.2's "Dark-Only Mode" preference over to the theme setting, once. */
    public void migrateLegacyDarkOnly() {
        if (prefs.contains(LEGACY_KEY_DARK_ONLY)) {
            setThemeMode(getThemeMode());
        }
    }

    /** From {@link SharedPreferences}; passes changes of this class's keys on to the {@link Listener}s. */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (key != null && KEYS.contains(key)) {
            for (Listener listener : listeners) {
                listener.onSettingChanged(key);
            }
        }
    }

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }
}
