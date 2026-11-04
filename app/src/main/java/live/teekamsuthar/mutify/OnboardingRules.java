package live.teekamsuthar.mutify;

/**
 * The decisions behind the onboarding dialog that depend only on the Android version and on who
 * installed Mutify. Kept free of Android types so it can be unit-tested on the JVM.
 */
public final class OnboardingRules {

    /** Package name of the Google Play Store, as reported as installer. */
    static final String PLAY_STORE = "com.android.vending";
    /** Android 11: the system can open the notification-access page of one app directly. */
    static final int SDK_OWN_ACCESS_PAGE = 30;
    /** Android 13: notification access becomes a "restricted setting" for sideloaded apps. */
    static final int SDK_RESTRICTED_SETTINGS = 33;

    private OnboardingRules() {
    }

    /** "Open settings" goes straight to Mutify's own notification-access page, not the list. */
    public static boolean opensOwnAccessPage(int sdkInt) {
        return sdkInt >= SDK_OWN_ACCESS_PAGE;
    }

    /**
     * The dialog explains "Allow restricted settings" when Android may grey out the access switch:
     * from Android 13 on, for an app not installed by the Play Store ({@code installer} is null for
     * an adb install or when unknown).
     */
    public static boolean showsRestrictedSettingsHint(int sdkInt, String installer) {
        return sdkInt >= SDK_RESTRICTED_SETTINGS && !PLAY_STORE.equals(installer);
    }
}
