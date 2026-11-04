package live.teekamsuthar.mutify;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Feeds Spotify's current track and play state to the UI through {@link ReceiverCallback}, read
 * from Spotify's MediaSession. Replaces {@code SpotifyBroadcastReceiver}, whose broadcasts newer
 * Spotify versions no longer send. Needs notification access, like {@link SpotifyAdListenerService}.
 */
class SpotifySessionWatcher {

    private static final String TAG = "SpotifySessionWatcher";

    private final Context context;
    private final ReceiverCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> watch(SpotifyAdListenerService.findSpotify(controllers));
    private final MediaController.Callback spotifyCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            publishMetadata();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            publishPlaybackState();
        }

        @Override
        public void onSessionDestroyed() {
            watch(null);
        }
    };

    @Nullable
    private MediaSessionManager sessionManager;
    @Nullable
    private MediaController spotify;

    SpotifySessionWatcher(Context context, ReceiverCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    void start() {
        if (sessionManager != null) {
            return;
        }
        MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener,
                    SpotifyAdListenerService.componentName(context), handler);
            sessionManager = manager;
            watch(SpotifyAdListenerService.findSpotify(
                    manager.getActiveSessions(SpotifyAdListenerService.componentName(context))));
        } catch (SecurityException e) {
            Log.w(TAG, "No notification access, cannot show Spotify's track", e);
        }
    }

    void stop() {
        if (sessionManager != null) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            sessionManager = null;
        }
        watch(null);
    }

    private void watch(@Nullable MediaController controller) {
        if (spotify != null && controller != null && spotify.getSessionToken().equals(controller.getSessionToken())) {
            return;
        }
        if (spotify != null) {
            spotify.unregisterCallback(spotifyCallback);
        }
        spotify = controller;
        if (spotify != null) {
            spotify.registerCallback(spotifyCallback, handler);
            publishMetadata();
            publishPlaybackState();
        }
    }

    private void publishMetadata() {
        Song song = currentSong();
        if (song != null) {
            callback.metadataChanged(song);
        }
    }

    private void publishPlaybackState() {
        Song song = currentSong();
        PlaybackState state = spotify == null ? null : spotify.getPlaybackState();
        if (song == null || state == null) {
            return;
        }
        callback.playbackStateChanged(state.getState() == PlaybackState.STATE_PLAYING,
                (int) state.getPosition(), song);
    }

    @Nullable
    private Song currentSong() {
        MediaMetadata metadata = spotify == null ? null : spotify.getMetadata();
        if (metadata == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        return new Song(
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                (int) metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                now,
                now);
    }
}
