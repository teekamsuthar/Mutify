package live.teekamsuthar.mutify;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThemeModeTest {

    @Test
    public void withoutAnySettingTheThemeFollowsTheSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null, false));
    }

    @Test
    public void darkOnlyModeFrom262BecomesDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored(null, true));
    }

    @Test
    public void aStoredThemeWinsOverTheOldDarkOnlyMode() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("light", true));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("system", true));
    }

    @Test
    public void everyThemeReadsBackWhatItStores() {
        for (ThemeMode mode : ThemeMode.values()) {
            assertEquals(mode, ThemeMode.fromStored(mode.storedValue(), false));
        }
    }

    @Test
    public void anUnknownStoredValueFollowsTheSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("sepia", false));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("", true));
    }

    @Test
    public void eachThemeMapsToItsNightMode() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.nightMode());
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.nightMode());
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.nightMode());
    }
}
