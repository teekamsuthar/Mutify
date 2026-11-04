package live.teekamsuthar.mutify;

import android.service.quicksettings.Tile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TileContentTest {

    @Test
    public void offIsInactiveWithoutSubtitle() {
        TileContent content = TileContent.of(MutifyStatus.OFF);
        assertEquals(Tile.STATE_INACTIVE, content.state);
        assertEquals(0, content.subtitle);
    }

    @Test
    public void onIsActiveWithoutSubtitle() {
        TileContent content = TileContent.of(MutifyStatus.ON);
        assertEquals(Tile.STATE_ACTIVE, content.state);
        assertEquals(0, content.subtitle);
    }

    @Test
    public void adMutedIsActiveWithAdMutedSubtitle() {
        TileContent content = TileContent.of(MutifyStatus.AD_MUTED);
        assertEquals(Tile.STATE_ACTIVE, content.state);
        assertEquals(R.string.status_ad_muted, content.subtitle);
    }

    @Test
    public void setupNeededLooksOffWithSetupNeededSubtitleAndStaysTappable() {
        // not STATE_UNAVAILABLE: SystemUI drops taps on unavailable tiles, and this tap opens the app
        TileContent content = TileContent.of(MutifyStatus.SETUP_NEEDED);
        assertEquals(Tile.STATE_INACTIVE, content.state);
        assertEquals(R.string.status_setup_needed, content.subtitle);
    }
}
