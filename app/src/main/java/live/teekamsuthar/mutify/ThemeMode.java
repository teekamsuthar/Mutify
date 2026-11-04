package live.teekamsuthar.mutify;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * The theme setting from the overflow menu: follow the system (default), always light or always
 * dark. Stored by {@link MutifySettings}, applied app-wide through AppCompat's night mode.
 * <p>
 * Kept free of Android types so it can be unit-tested on the JVM: it uses only AppCompatDelegate's
 * night-mode constants and annotation, which are compiled into this class, never loaded.
 */
public enum ThemeMode {
    SYSTEM("system", MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", MODE_NIGHT_NO),
    DARK("dark", MODE_NIGHT_YES);

    private final String storedValue;
    @AppCompatDelegate.NightMode
    private final int nightMode;

    ThemeMode(String storedValue, @AppCompatDelegate.NightMode int nightMode) {
        this.storedValue = storedValue;
        this.nightMode = nightMode;
    }

    /** The value kept in the preferences. */
    public String storedValue() {
        return storedValue;
    }

    /** The AppCompatDelegate night mode that shows this theme. */
    @AppCompatDelegate.NightMode
    public int nightMode() {
        return nightMode;
    }

    /**
     * Reads the stored setting. Without one, 2.6.2's "Dark-Only Mode" carries over: switched on
     * it becomes {@link #DARK}, otherwise the theme follows the system. An unknown value (e.g.
     * written by a later version) follows the system too.
     *
     * @param stored         the stored value, or null when the setting was never written
     * @param legacyDarkOnly 2.6.2's "Dark-Only Mode" preference
     */
    public static ThemeMode fromStored(String stored, boolean legacyDarkOnly) {
        if (stored == null) {
            return legacyDarkOnly ? DARK : SYSTEM;
        }
        for (ThemeMode mode : values()) {
            if (mode.storedValue.equals(stored)) {
                return mode;
            }
        }
        return SYSTEM;
    }
}
