package live.teekamsuthar.mutify;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The "Mutify" Quick Settings tile. Shows the current {@link MutifyStatus} and switches muting on
 * or off with a tap; without notification access a tap opens the app's onboarding instead, so the
 * tile never pretends to work.
 * <p>
 * A standard (not active) tile: the system binds it whenever the panel opens, and
 * {@link StatusPublisher} asks for an update through {@link #requestUpdate(Context)} whenever the
 * status changes while it is open.
 */
@RequiresApi(Build.VERSION_CODES.N)
public class MutifyTileService extends TileService {

    private static final String TAG = "MutifyTile";
    /** Set once the in-app "add the tile" offer was made, whatever the user answered. */
    private static final String KEY_ADD_TILE_OFFERED = "add_tile_offered";

    private final StatusPublisher.Listener statusListener = this::render;

    @Override
    public void onStartListening() {
        super.onStartListening();
        StatusPublisher publisher = StatusPublisher.get(this);
        publisher.addListener(statusListener);
        render(publisher.currentStatus());
    }

    @Override
    public void onStopListening() {
        StatusPublisher.get(this).removeListener(statusListener);
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        StatusPublisher publisher = StatusPublisher.get(this);
        MutifyStatus status = publisher.currentStatus();
        if (status == MutifyStatus.SETUP_NEEDED) {
            // never write the setting without access: open the app straight into the onboarding
            if (isLocked()) {
                unlockAndRun(this::openApp);
            } else {
                openApp();
            }
            return;
        }
        // muting ads is harmless, so the tile also toggles from the lock screen without unlocking
        MutifySettings.get(this).setEnabled(!status.isOn());
        render(publisher.refresh());
    }

    private void render(@NonNull MutifyStatus status) {
        Tile tile = getQsTile();
        if (tile == null) {
            return; // not bound to a tile right now
        }
        TileContent content = TileContent.of(status);
        String label = getString(R.string.app_name);
        String subtitle = content.subtitle == 0 ? null : getString(content.subtitle);
        tile.setState(content.state);
        tile.setLabel(label);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_mutify));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(subtitle);
        }
        // read out by TalkBack; before Android 10 also the only place the subtitle appears
        tile.setContentDescription(subtitle == null ? label : label + ", " + subtitle);
        tile.updateTile();
    }

    // opens the main screen with the onboarding dialog and collapses the panel
    // the Intent variant is deprecated (and throws from Android 14 on) but is the only one before 14
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent intent = Onboarding.openAppIntent(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    /**
     * Asks the system to bind the tile so it shows the new status. Called by
     * {@link StatusPublisher} on every status change.
     */
    static void requestUpdate(@NonNull Context context) {
        try {
            requestListeningState(context, new ComponentName(context, MutifyTileService.class));
        } catch (RuntimeException e) {
            // some builds throw when the tile is not added; nothing to refresh then
            Log.w(TAG, "Could not request a tile update", e);
        }
    }

    /**
     * Offers once, on Android 13+, to add the tile to Quick Settings. The system skips its dialog
     * when the tile is already added.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    static void offerToAddOnce(@NonNull Activity activity) {
        SharedPreferences prefs = Utils.getPrefs(activity);
        if (prefs.getBoolean(KEY_ADD_TILE_OFFERED, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_ADD_TILE_OFFERED, true).apply();
        StatusBarManager statusBarManager = activity.getSystemService(StatusBarManager.class);
        if (statusBarManager == null) {
            return;
        }
        statusBarManager.requestAddTileService(
                new ComponentName(activity, MutifyTileService.class),
                activity.getString(R.string.app_name),
                Icon.createWithResource(activity, R.drawable.ic_tile_mutify),
                activity.getMainExecutor(),
                result -> Log.d(TAG, "Add tile request result: " + result));
    }
}
