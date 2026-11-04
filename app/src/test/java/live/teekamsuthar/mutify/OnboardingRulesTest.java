package live.teekamsuthar.mutify;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OnboardingRulesTest {

    @Test
    public void fromAndroid11OnTheOwnAccessPageOpens() {
        assertTrue(OnboardingRules.opensOwnAccessPage(30));
        assertTrue(OnboardingRules.opensOwnAccessPage(36));
    }

    @Test
    public void belowAndroid11TheListenerListOpens() {
        assertFalse(OnboardingRules.opensOwnAccessPage(21));
        assertFalse(OnboardingRules.opensOwnAccessPage(29));
    }

    @Test
    public void playInstallsNeverGetTheRestrictedSettingsHint() {
        assertFalse(OnboardingRules.showsRestrictedSettingsHint(33, "com.android.vending"));
        assertFalse(OnboardingRules.showsRestrictedSettingsHint(36, "com.android.vending"));
    }

    @Test
    public void sideloadedInstallsFromAndroid13OnGetTheHint() {
        // adb or unknown installer
        assertTrue(OnboardingRules.showsRestrictedSettingsHint(33, null));
        // a browser or file manager installing the APK
        assertTrue(OnboardingRules.showsRestrictedSettingsHint(36, "com.google.android.packageinstaller"));
    }

    @Test
    public void beforeAndroid13ThereAreNoRestrictedSettings() {
        assertFalse(OnboardingRules.showsRestrictedSettingsHint(21, null));
        assertFalse(OnboardingRules.showsRestrictedSettingsHint(32, null));
    }
}
