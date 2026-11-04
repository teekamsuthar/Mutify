package live.teekamsuthar.mutify;

/**
 * What Mutify is doing right now, as every surface (main screen, tile, widget) shows it.
 * <p>
 * Derived from three facts only — the stored "enabled" setting, whether the user granted
 * notification access, and whether an ad is being muted — so all surfaces always agree.
 * Kept free of Android types so it can be unit-tested on the JVM.
 */
public enum MutifyStatus {
    /** Notification access is missing: Mutify cannot see Spotify, whatever the setting says. */
    SETUP_NEEDED,
    /** Access granted, muting switched off. */
    OFF,
    /** Muting switched on, no ad playing. */
    ON,
    /** Muting switched on and an ad is being muted right now. */
    AD_MUTED;

    public static MutifyStatus of(boolean enabled, boolean hasNotificationAccess, boolean adActive) {
        if (!hasNotificationAccess) {
            return SETUP_NEEDED;
        }
        if (!enabled) {
            return OFF;
        }
        return adActive ? AD_MUTED : ON;
    }

    /** Mutify is switched on and able to mute (ON or AD_MUTED). */
    public boolean isOn() {
        return this == ON || this == AD_MUTED;
    }
}
