package live.teekamsuthar.mutify;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdDetectorTest {

    private static final long SONG_MS = 215_000;
    private static final long AD_MS = 30_000;

    @Test
    public void advertisementFlagMarksAnAd() {
        assertTrue(AdDetector.isAd(1L, "spotify:track:4uLU6hMCjMI75M1A2tKUQC", SONG_MS, "Some Brand", "Some Brand"));
    }

    @Test
    public void spotifyAdUriMarksAnAd() {
        assertTrue(AdDetector.isAd(0L, "spotify:ad:000000012c8e8f9e0000002011d6a2b4", SONG_MS, "Some Brand"));
        assertTrue(AdDetector.isAd(0L, "SPOTIFY:AD:abc", 0L));
    }

    @Test
    public void localizedAdvertisementLabelMarksAShortItemAsAd() {
        assertTrue(AdDetector.isAd(0L, null, AD_MS, "Advertisement", "Some Brand"));
        assertTrue(AdDetector.isAd(0L, null, AD_MS, null, "Werbung"));
        assertTrue(AdDetector.isAd(0L, null, 0L, " publicite "));   // accent and case folded, unknown duration
        assertTrue(AdDetector.isAd(0L, null, AD_MS, "Quang cao"));
        assertTrue(AdDetector.isAd(0L, null, AD_MS, "광고"));
    }

    @Test
    public void labelWithNothingElseMarksAnAdOfUnknownDuration() {
        // the media notification has no duration: title, text, sub-text
        assertTrue(AdDetector.isAd(0L, null, 0L, "Advertisement", null, ""));
        assertTrue(AdDetector.isAd(0L, null, 0L, "Werbung", "Spotify", null));
        assertTrue(AdDetector.isAd(0L, null, -1L, null, "Advertisement"));
    }

    @Test
    public void labelAsSongTitleArtistOrAlbumOfUnknownDurationIsNoAd() {
        assertFalse(AdDetector.isAd(0L, null, 0L, "Propaganda", "Sparks", "Propaganda"));
        assertFalse(AdDetector.isAd(0L, null, 0L, "Dr. Mabuse", "Propaganda", "A Secret Wish"));
        assertFalse(AdDetector.isAd(0L, null, 0L, "Duel", "Propaganda", "Propaganda"));
        assertFalse(AdDetector.isAd(0L, null, 0L, "Lost in the Rain", "Some Band", "Werbung"));
    }

    @Test
    public void strongSignalMarksAnAdOfUnknownDuration() {
        assertTrue(AdDetector.isAd(1L, null, 0L, "Some Brand", "Some Brand"));
        assertTrue(AdDetector.isAd(0L, "spotify:ad:abc", 0L, "Propaganda", "Sparks", "Propaganda"));
    }

    @Test
    public void normalTrackIsNoAd() {
        assertFalse(AdDetector.isAd(0L, "spotify:track:4uLU6hMCjMI75M1A2tKUQC", SONG_MS, "Bohemian Rhapsody", "Queen", "A Night at the Opera"));
        assertFalse(AdDetector.isAd(0L, null, 0L));
        assertFalse(AdDetector.isAd(0L, null, 0L, (CharSequence) null));
    }

    @Test
    public void songTitledLikeTheAdLabelIsNoAd() {
        // "Propaganda" is Spotify's Portuguese label, and also a real song title
        assertFalse(AdDetector.isAd(0L, "spotify:track:1", SONG_MS, "Propaganda", "Muse"));
    }

    @Test
    public void labelMustBeTheWholeText() {
        assertFalse(AdDetector.isAd(0L, null, AD_MS, "Advertisement Blues"));
        assertFalse(AdDetector.isAd(0L, null, AD_MS, "Spotify"));
    }
}
