package live.teekamsuthar.mutify;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import static live.teekamsuthar.mutify.Utils.SPOTIFY_PACKAGE;

/**
 * Mutes the music stream while Spotify plays an ad.
 * <p>
 * Replaces the old approach that listened to Spotify's "Device Broadcast Status" intents, which
 * newer Spotify versions no longer send. A notification listener is the only way for a normal
 * app to read another app's MediaSession: once the user grants notification access, Android
 * binds this service and lets it follow Spotify's session — the same metadata the lock screen
 * shows — and {@link AdDetector} decides from it whether the current item is an ad.
 * <p>
 * The system keeps this service bound independently of {@link MainActivity}; it only mutes while
 * the user has the "Mute Ads" switch on ({@link #isMutingEnabled(Context)}).
 */
public class SpotifyAdListenerService extends NotificationListenerService {

    private static final String TAG = "SpotifyAdListener";
    private static final String PREF_MUTING_ENABLED = "mute_ads_enabled";
    /** Wait a moment after an ad before unmuting, so its last bit does not leak out. */
    private static final long UNMUTE_DELAY_MS = 600;

    @Nullable
    private static SpotifyAdListenerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable unmuteRunnable = this::endAdMute;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> {
                watchSpotify(findSpotify(controllers));
                evaluate();
            };
    private final MediaController.Callback spotifyCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            evaluate();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            evaluate();
        }

        @Override
        public void onSessionDestroyed() {
            watchSpotify(null);
            evaluate();
        }
    };

    private AudioManager audioManager;
    private MediaSessionManager sessionManager;
    @Nullable
    private MediaController spotify;
    /** Last Spotify notification, used only while no Spotify session is visible. */
    @Nullable
    private Notification spotifyNotification;
    @Nullable
    private String spotifyNotificationKey;
    /** An ad is playing (or just ended and the delayed unmute is pending). */
    private boolean adMuteActive;
    /** This service muted the stream, so it may unmute it — never undo a mute the user made. */
    private boolean mutedByUs;

    // ---- static API for the UI -------------------------------------------------------------

    public static boolean isMutingEnabled(Context context) {
        return Utils.getPrefs(context).getBoolean(PREF_MUTING_ENABLED, false);
    }

    /** Turns ad muting on or off; turning it off during an ad unmutes right away. */
    public static void setMutingEnabled(Context context, boolean enabled) {
        Utils.getPrefs(context).edit().putBoolean(PREF_MUTING_ENABLED, enabled).apply();
        SpotifyAdListenerService service = instance;
        if (service != null) {
            service.handler.post(service::evaluate);
        }
    }

    public static boolean hasNotificationAccess(Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.getPackageName());
    }

    public static ComponentName componentName(Context context) {
        return new ComponentName(context, SpotifyAdListenerService.class);
    }

    @Nullable
    static MediaController findSpotify(@Nullable List<MediaController> controllers) {
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (SPOTIFY_PACKAGE.equals(controller.getPackageName())) {
                    return controller;
                }
            }
        }
        return null;
    }

    // ---- listener lifecycle ----------------------------------------------------------------

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        sessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            ComponentName component = componentName(this);
            sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, component, handler);
            watchSpotify(findSpotify(sessionManager.getActiveSessions(component)));
        } catch (SecurityException e) {
            // notification access was revoked between binding and this call
            Log.w(TAG, "Cannot read media sessions", e);
        }
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                onNotificationPosted(sbn);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot read active notifications", e);
        }
        evaluate();
        Log.i(TAG, "Connected, watching Spotify: " + (spotify != null));
    }

    @Override
    public void onListenerDisconnected() {
        if (sessionManager != null) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        }
        watchSpotify(null);
        spotifyNotification = null;
        spotifyNotificationKey = null;
        handler.removeCallbacks(unmuteRunnable);
        endAdMute();
        if (instance == this) {
            instance = null;
        }
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !SPOTIFY_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras != null && extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            spotifyNotification = notification;
            spotifyNotificationKey = sbn.getKey();
            if (spotify == null) {
                // the session list can lag behind the notification; its token gives us the session now
                MediaSession.Token token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
                if (token != null) {
                    watchSpotify(new MediaController(this, token));
                }
            }
        }
        evaluate();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && SPOTIFY_PACKAGE.equals(sbn.getPackageName())
                && sbn.getKey().equals(spotifyNotificationKey)) {
            spotifyNotification = null;
            spotifyNotificationKey = null;
            evaluate();
        }
    }

    private void watchSpotify(@Nullable MediaController controller) {
        if (spotify != null && controller != null && spotify.getSessionToken().equals(controller.getSessionToken())) {
            return;
        }
        if (spotify != null) {
            spotify.unregisterCallback(spotifyCallback);
        }
        spotify = controller;
        if (spotify != null) {
            spotify.registerCallback(spotifyCallback, handler);
        }
    }

    // ---- ad handling -----------------------------------------------------------------------

    private void evaluate() {
        if (audioManager == null) {
            return; // not connected yet
        }
        boolean ad = isMutingEnabled(this) && isAdPlaying();
        if (ad) {
            handler.removeCallbacks(unmuteRunnable);
            if (!adMuteActive) {
                beginAdMute();
            }
        } else if (adMuteActive) {
            handler.removeCallbacks(unmuteRunnable);
            handler.postDelayed(unmuteRunnable, isMutingEnabled(this) ? UNMUTE_DELAY_MS : 0);
        }
    }

    // lint only knows the documented long keys; the ADVERTISEMENT key is real but not in its list
    @SuppressLint("WrongConstant")
    private boolean isAdPlaying() {
        if (spotify != null) {
            MediaMetadata metadata = spotify.getMetadata();
            if (metadata == null) {
                return false;
            }
            boolean ad = AdDetector.isAd(
                    metadata.getLong(AdDetector.METADATA_KEY_ADVERTISEMENT),
                    metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
            Log.v(TAG, "Spotify item '" + metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    + "' id=" + metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) + " ad=" + ad);
            return ad;
        }
        if (spotifyNotification != null && spotifyNotification.extras != null) {
            // no session visible: fall back to the text of Spotify's media notification
            Bundle extras = spotifyNotification.extras;
            return AdDetector.isAd(0L, null, 0L,
                    extras.getCharSequence(Notification.EXTRA_TITLE),
                    extras.getCharSequence(Notification.EXTRA_TEXT),
                    extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        }
        return false;
    }

    private void beginAdMute() {
        adMuteActive = true;
        if (isMusicMuted()) {
            Log.i(TAG, "Ad detected, music already muted by the user");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        } else {
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
        }
        mutedByUs = true;
        Log.i(TAG, "Ad detected, muted");
    }

    private void endAdMute() {
        if (mutedByUs && audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            }
            Log.i(TAG, "Ad over, unmuted");
        }
        mutedByUs = false;
        adMuteActive = false;
    }

    private boolean isMusicMuted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            return true;
        }
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
    }
}
