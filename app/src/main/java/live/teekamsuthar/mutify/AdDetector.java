package live.teekamsuthar.mutify;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether what Spotify currently plays is an ad, from the metadata Spotify publishes on
 * its MediaSession / media notification. Spotify offers no API for this, so it combines three
 * signals, strongest first:
 * <ol>
 *     <li>the standard {@code android.media.metadata.ADVERTISEMENT} flag set to 1,</li>
 *     <li>a media ID with Spotify's ad URI scheme ({@code spotify:ad:...}),</li>
 *     <li>a title/artist/notification text that is exactly Spotify's localized "Advertisement"
 *     label — only trusted for short items, so a real song called e.g. "Propaganda" or
 *     "Werbung" is not muted. When the duration is unknown (the media notification has none),
 *     the label counts only if no text names a song, artist or album: every text is the label,
 *     Spotify's own name or empty. A band called "Propaganda", or Sparks' "Propaganda" on the
 *     album "Propaganda", is then still no ad.</li>
 * </ol>
 * Plain Java on purpose, so it can be unit tested without Android.
 */
final class AdDetector {

    static final String METADATA_KEY_ADVERTISEMENT = "android.media.metadata.ADVERTISEMENT";
    static final String SPOTIFY_AD_URI_PREFIX = "spotify:ad:";

    /** Items longer than this are songs, whatever their title says. Spotify ads run 15–60 s. */
    static final long MAX_TEXT_MATCHED_AD_DURATION_MS = 90_000;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final String SPOTIFY_NAME = "spotify";

    /**
     * Spotify's translations of its "Advertisement" label (resource {@code string/advertisement}),
     * as shipped in the Spotify APK. Matched case- and accent-insensitively.
     */
    private static final Set<String> AD_LABELS = new HashSet<>();

    static {
        for (String label : Arrays.asList(
                "Advertensie", "Advertentie", "Advertisement", "Annonse", "Anunci", "Anuncio",
                "Anunț", "Anúncio", "Auglýsing", "Iklan", "Iragarkia", "Isikhangiso", "Mainos",
                "Oglas", "Propaganda", "Pubblicità", "Publicidad", "Publicidade", "Publicité",
                "Quảng cáo", "Reklaam", "Reklam", "Reklama", "Reklame", "Reklám", "Reklāma",
                "Tangazo", "Werbung", "Διαφήμιση", "Реклама", "פרסומת", "آگهی", "إشهار", "إعلان",
                "اشتہار", "जाहिरात", "विज्ञापन", "বিজ্ঞাপন", "ਵਿਗਿਆਪਨ", "જાહેરાત", "ବିଜ୍ଞାପନ",
                "விளம்பரம்", "ప్రకటన", "ಜಾಹೀರಾತು", "പരസ്യം", "โฆษณา", "ማስታወቂያ", "广告", "広告",
                "廣告", "광고")) {
            AD_LABELS.add(fold(label));
        }
    }

    private AdDetector() {
    }

    /**
     * @param advertisementFlag value of {@link #METADATA_KEY_ADVERTISEMENT} (0 when absent)
     * @param mediaId           {@code METADATA_KEY_MEDIA_ID}, may be null
     * @param durationMs        {@code METADATA_KEY_DURATION}, 0 or negative when unknown
     * @param texts             title, artist, album, notification title/text/sub-text … (nulls allowed)
     */
    static boolean isAd(long advertisementFlag, String mediaId, long durationMs, CharSequence... texts) {
        if (advertisementFlag == 1L) {
            return true;
        }
        if (mediaId != null && mediaId.regionMatches(true, 0, SPOTIFY_AD_URI_PREFIX, 0, SPOTIFY_AD_URI_PREFIX.length())) {
            return true;
        }
        if (durationMs > MAX_TEXT_MATCHED_AD_DURATION_MS) {
            return false;
        }
        boolean label = false;
        boolean songText = false;
        for (CharSequence text : texts) {
            if (isAdLabel(text)) {
                label = true;
            } else if (!isBlank(text) && !isSpotifyName(text)) {
                songText = true;
            }
        }
        if (durationMs > 0) {
            return label; // a short item: the label alone is enough
        }
        // unknown duration: the label alone could be a song's title, artist or album
        return label && !songText;
    }

    /** True when the whole text is Spotify's "Advertisement" label in any language. */
    static boolean isAdLabel(CharSequence text) {
        return text != null && AD_LABELS.contains(fold(text.toString()));
    }

    private static boolean isBlank(CharSequence text) {
        return text == null || text.toString().trim().isEmpty();
    }

    /** Spotify names itself as the artist of some ads (seen by ad-silence). */
    private static boolean isSpotifyName(CharSequence text) {
        return SPOTIFY_NAME.equals(fold(text.toString()));
    }

    private static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }
}
