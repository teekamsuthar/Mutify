package live.teekamsuthar.mutify;

import java.util.Locale;

/**
 * The app languages offered in the overflow menu ("Language"), next to "System default".
 * <p>
 * The choice itself is stored by AndroidX ({@code AppCompatDelegate.setApplicationLocales}): on
 * Android 13+ it is the same per-app language the system settings show, below that AppCompat keeps
 * it. Kept free of Android types so the mapping can be unit-tested on the JVM.
 */
public enum AppLanguage {
    /** Follow the phone's language. */
    SYSTEM(""),
    ENGLISH("en"),
    GERMAN("de"),
    SPANISH("es"),
    FRENCH("fr"),
    PORTUGUESE_BRAZIL("pt-BR"),
    VIETNAMESE("vi");

    /** BCP 47 tag passed to AppCompat; empty for {@link #SYSTEM}. */
    public final String tag;

    AppLanguage(String tag) {
        this.tag = tag;
    }

    /**
     * The menu entry for the app locales currently set, given as comma-separated language tags
     * (e.g. "de-AT,en"; empty when the app follows the system). Regional variants map to their
     * language ("de-AT" → German, "es-419" → Spanish, any "pt" → Brazilian Portuguese); a language
     * Mutify does not ship counts as "System default".
     */
    public static AppLanguage fromTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) {
            return SYSTEM;
        }
        String first = tags.split(",")[0].trim();
        switch (first.split("[-_]")[0].toLowerCase(Locale.ROOT)) {
            case "en":
                return ENGLISH;
            case "de":
                return GERMAN;
            case "es":
                return SPANISH;
            case "fr":
                return FRENCH;
            case "pt":
                return PORTUGUESE_BRAZIL;
            case "vi":
                return VIETNAMESE;
            default:
                return SYSTEM;
        }
    }
}
