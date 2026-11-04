package live.teekamsuthar.mutify;

import android.os.Build;
import android.service.quicksettings.Tile;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;

/**
 * What the Quick Settings tile shows for a {@link MutifyStatus}: the tile state and an optional
 * subtitle. Kept apart from {@link MutifyTileService} so the mapping can be unit-tested on the JVM
 * (the {@link Tile} state constants are compile-time constants).
 */
@RequiresApi(Build.VERSION_CODES.N)
final class TileContent {

    /** {@link Tile#STATE_ACTIVE} or {@link Tile#STATE_INACTIVE}. */
    final int state;
    /** The subtitle's string resource, or 0 for none. */
    @StringRes
    final int subtitle;

    private TileContent(int state, @StringRes int subtitle) {
        this.state = state;
        this.subtitle = subtitle;
    }

    static TileContent of(MutifyStatus status) {
        switch (status) {
            case SETUP_NEEDED:
                // shown as off, not STATE_UNAVAILABLE: SystemUI ignores taps on an unavailable tile,
                // and a tap here must open the app to finish the setup
                return new TileContent(Tile.STATE_INACTIVE, R.string.status_setup_needed);
            case ON:
                return new TileContent(Tile.STATE_ACTIVE, 0);
            case AD_MUTED:
                return new TileContent(Tile.STATE_ACTIVE, R.string.status_ad_muted);
            case OFF:
            default:
                return new TileContent(Tile.STATE_INACTIVE, 0);
        }
    }
}
