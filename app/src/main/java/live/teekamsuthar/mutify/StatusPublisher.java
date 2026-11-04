package live.teekamsuthar.mutify;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one place that pushes Mutify's {@link MutifyStatus} out to every surface that shows it.
 * <p>
 * Inputs: the "enabled" setting (watched through {@link MutifySettings}), notification access
 * (re-checked on every {@link #refresh()}, which the listener service calls when it connects or
 * disconnects and the main screen calls on resume) and the ad state, which
 * {@link SpotifyAdListenerService} reports through {@link #setAdActive(boolean)}.
 * <p>
 * Outputs: the {@link Listener}s registered by open screens. Surfaces that are not screens of
 * this app — a Quick Settings tile, a home-screen widget — belong in {@link #publish(MutifyStatus)}
 * too, so they are refreshed whether or not a screen is open.
 * <p>
 * Everything runs on the main thread.
 */
public final class StatusPublisher {

    private static final String TAG = "MutifyStatus";

    public interface Listener {
        void onStatusChanged(@NonNull MutifyStatus status);
    }

    // holds the application context only, which lives as long as the process
    @SuppressLint("StaticFieldLeak")
    @Nullable
    private static StatusPublisher instance;

    private final Context context;
    private final MutifySettings settings;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final MutifySettings.Listener settingsListener = key -> {
        if (MutifySettings.KEY_ENABLED.equals(key)) {
            refresh();
        }
    };
    private boolean adActive;
    @Nullable
    private MutifyStatus lastPublished;

    private StatusPublisher(Context context) {
        this.context = context.getApplicationContext();
        settings = MutifySettings.get(this.context);
        settings.addListener(settingsListener);
    }

    public static synchronized StatusPublisher get(Context context) {
        if (instance == null) {
            instance = new StatusPublisher(context);
        }
        return instance;
    }

    /** The status as of now, without publishing it. */
    @NonNull
    public MutifyStatus currentStatus() {
        return MutifyStatus.of(settings.isEnabled(),
                SpotifyAdListenerService.hasNotificationAccess(context), adActive);
    }

    /**
     * Called by the listener service after it started or stopped muting an ad. Always publishes,
     * even when the status stays the same (switched off mid-ad: OFF before and after the unmute),
     * so screens can show that the stream was just unmuted.
     */
    @MainThread
    void setAdActive(boolean active) {
        if (adActive != active) {
            adActive = active;
            lastPublished = currentStatus();
            publish(lastPublished);
        }
    }

    /**
     * Re-reads all inputs and publishes the status if it changed since the last publish.
     *
     * @return the current status
     */
    @MainThread
    @NonNull
    public MutifyStatus refresh() {
        MutifyStatus status = currentStatus();
        if (status != lastPublished) {
            lastPublished = status;
            publish(status);
        }
        return status;
    }

    @MainThread
    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @MainThread
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    private void publish(@NonNull MutifyStatus status) {
        Log.d(TAG, "Status " + status + " to " + listeners.size() + " screen(s), tile and widgets");
        for (Listener listener : listeners) {
            listener.onStatusChanged(status);
        }
        // the Quick Settings tile (Android 7+) re-reads the status when the system binds it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MutifyTileService.requestUpdate(context);
        }
        MutifyWidgetProvider.updateAll(context, status);
    }
}
