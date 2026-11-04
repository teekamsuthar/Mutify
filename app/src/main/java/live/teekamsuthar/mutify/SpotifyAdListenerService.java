package live.teekamsuthar.mutify;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.core.content.ContextCompat;

import java.util.List;

import static live.teekamsuthar.mutify.Utils.SPOTIFY_PACKAGE;

/**
 * Mutes the music stream while Spotify plays an ad, or plays the ad quieter
 * ({@link MutifySettings#getAdVolumeStep()}; the rules are in {@link AdVolumeSession}).
 * <p>
 * Replaces the old approach that listened to Spotify's "Device Broadcast Status" intents, which
 * newer Spotify versions no longer send. A notification listener is the only way for a normal
 * app to read another app's MediaSession: once the user grants notification access, Android
 * binds this service and lets it follow Spotify's session — the same metadata the lock screen
 * shows — and {@link AdDetector} decides from it whether the current item is an ad.
 * <p>
 * The system keeps this service bound independently of {@link MainActivity}, so muting goes on
 * with the app closed or swiped away. It only mutes while Mutify is switched on
 * ({@link MutifySettings#isEnabled()}), follows that setting live, and reports ad start and end to
 * {@link StatusPublisher}.
 * <p>
 * While an ad plays it follows the music volume and keeps the ad at the chosen step: a volume key
 * pressed during the ad, or the stream being unmuted, is answered by applying the step again, and a
 * new step chosen during the ad applies to that ad at once. Afterwards the volume from before the ad
 * comes back. An ad counts only while Spotify plays it: paused or stopped on an ad, the volume comes
 * back, and it is lowered again when the ad resumes.
 */
public class SpotifyAdListenerService extends NotificationListenerService {

    private static final String TAG = "SpotifyAdListener";
    /** Wait a moment after an ad before unmuting, so its last bit does not leak out. */
    private static final long UNMUTE_DELAY_MS = 600;
    /** Sent by the system when a stream's volume changes; not public API, but sent for many years. */
    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    /** Sent by the system when a stream is muted or unmuted; not public API either. */
    private static final String ACTION_STREAM_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable adEndRunnable = this::endAd;
    private final AdVolumeSession adVolume = new AdVolumeSession();
    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (adVolume.isActive() && audioManager != null) {
                int volume = musicVolume();
                // Mutify's own change, seen here too, matches the step and needs nothing
                AdVolumeSession.Action correction = adVolume.observe(volume);
                if (correction.kind != AdVolumeSession.Kind.NONE) {
                    apply(correction);
                    Log.i(TAG, "Volume changed to " + volume + " during an ad: " + correction);
                }
            }
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> {
                watchSpotify(findSpotify(controllers));
                evaluate();
            };
    private final MutifySettings.Listener settingsListener = key -> {
        Log.i(TAG, "Setting changed: " + key);
        if (MutifySettings.KEY_ENABLED.equals(key)) {
            evaluate();
        } else if (MutifySettings.KEY_AD_VOLUME_STEP.equals(key)) {
            changeAdVolumeStep();
        }
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

    private MutifySettings settings;
    private StatusPublisher statusPublisher;
    private AudioManager audioManager;
    private MediaSessionManager sessionManager;
    @Nullable
    private MediaController spotify;
    /** Last Spotify notification, used only while no Spotify session is visible. */
    @Nullable
    private Notification spotifyNotification;
    @Nullable
    private String spotifyNotificationKey;
    /** The ad is over and {@link #endAd()} is scheduled; scheduled once per ad end, not per callback. */
    private boolean adEndPending;
    private boolean volumeReceiverRegistered;

    // ---- static API for the UI -------------------------------------------------------------

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
        settings = MutifySettings.get(this);
        statusPublisher = StatusPublisher.get(this);
        settings.addListener(settingsListener);
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
        statusPublisher.refresh(); // notification access may just have been granted
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
        endAd();
        if (settings != null) {
            settings.removeListener(settingsListener);
        }
        if (statusPublisher != null) {
            statusPublisher.refresh(); // notification access may just have been revoked
        }
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        if (adVolume.isActive() || adEndPending) {
            // destroyed without onListenerDisconnected, maybe inside the unmute delay: restore now,
            // before the pending end is cleared with the handler's callbacks below
            endAd();
        }
        unwatchVolume();
        if (settings != null) {
            settings.removeListener(settingsListener);
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
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
        boolean enabled = settings.isEnabled();
        boolean ad = enabled && isAdPlaying();
        if (ad) {
            if (adEndPending) {
                // the next ad started before the last one's end was carried out: one session goes on
                handler.removeCallbacks(adEndRunnable);
                adEndPending = false;
            }
            if (!adVolume.isActive()) {
                beginAd();
            }
        } else if (adVolume.isActive()) {
            if (!enabled) {
                // switched off during an ad: sound back right away
                Log.i(TAG, "Switched off during an ad: ending it now");
                endAd();
            } else if (!adEndPending) {
                // once per ad end: later callbacks (position, metadata) must not push the end back
                adEndPending = true;
                handler.postDelayed(adEndRunnable, UNMUTE_DELAY_MS);
            }
        }
    }

    // lint only knows the documented long keys; the ADVERTISEMENT key is real but not in its list
    @SuppressLint("WrongConstant")
    private boolean isAdPlaying() {
        MediaMetadata metadata = null;
        if (spotify != null) {
            if (!isPlaying(spotify.getPlaybackState())) {
                // paused or stopped on an ad: the rest of the phone must not stay muted meanwhile
                return false;
            }
            metadata = spotify.getMetadata();
        }
        if (metadata != null) {
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
            // no session visible, or it has no metadata: fall back to the text of Spotify's media
            // notification (it has no duration, so AdDetector asks for more than the label)
            Bundle extras = spotifyNotification.extras;
            boolean ad = AdDetector.isAd(0L, null, 0L,
                    extras.getCharSequence(Notification.EXTRA_TITLE),
                    extras.getCharSequence(Notification.EXTRA_TEXT),
                    extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            Log.v(TAG, "Spotify notification '" + extras.getCharSequence(Notification.EXTRA_TITLE) + "' ad=" + ad);
            return ad;
        }
        return false;
    }

    /**
     * Spotify plays, or is about to (buffering, connecting); unknown counts as playing. An ad
     * that is paused or stopped is no reason to keep the music stream muted.
     */
    private static boolean isPlaying(@Nullable PlaybackState state) {
        if (state == null) {
            return true;
        }
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
                return true;
            default:
                return false;
        }
    }

    private void beginAd() {
        int volume = musicVolume();
        int step = settings.getAdVolumeStep();
        AdVolumeSession.Action action =
                adVolume.begin(volume, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), step);
        apply(action);
        watchVolume(); // after applying, so the change Mutify just made is the expected one
        Log.i(TAG, "Ad detected at volume " + volume + ", step " + step + ": " + action);
        statusPublisher.setAdActive(true); // after muting, so surfaces see the muted stream
    }

    /** The user picked another ad volume step; applies it to the running ad, if any. */
    private void changeAdVolumeStep() {
        if (!adVolume.isActive() || audioManager == null) {
            return;
        }
        int volume = musicVolume();
        int step = settings.getAdVolumeStep();
        AdVolumeSession.Action action = adVolume.changeStep(volume, step);
        apply(action);
        Log.i(TAG, "Ad volume step changed to " + step + " during an ad at volume " + volume + ": " + action);
    }

    /** The ad is over, or Mutify was switched off or lost notification access during it. */
    private void endAd() {
        handler.removeCallbacks(adEndRunnable);
        adEndPending = false;
        unwatchVolume();
        if (adVolume.isActive() && audioManager != null) {
            int volume = musicVolume();
            AdVolumeSession.Action action = adVolume.end(volume);
            apply(action);
            Log.i(TAG, "Ad over at volume " + volume + ": " + action);
        }
        if (statusPublisher != null) {
            statusPublisher.setAdActive(false);
        }
    }

    private void apply(AdVolumeSession.Action action) {
        switch (action.kind) {
            case MUTE:
                setMusicMuted(true);
                break;
            case UNMUTE:
                setMusicMuted(false);
                break;
            case SET_VOLUME:
            case RESTORE:
                setMusicVolume(action.volume);
                break;
            case UNMUTE_AND_SET_VOLUME:
            case UNMUTE_AND_RESTORE:
                setMusicVolume(action.volume); // first, so the volume the stream was muted at never plays
                setMusicMuted(false);
                break;
            default:
                break;
        }
    }

    private void setMusicVolume(int volume) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        } catch (SecurityException e) {
            // volume changes can be refused, e.g. in some Do Not Disturb modes
            Log.w(TAG, "Cannot set the music volume", e);
        }
    }

    private void setMusicMuted(boolean muted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } else {
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, muted);
        }
    }

    /** The music volume as the user hears it: 0 while the stream is muted. */
    private int musicVolume() {
        return isMusicMuted() ? 0 : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    /** Follows volume changes during an ad, so the ad stays at the chosen step. */
    private void watchVolume() {
        if (!volumeReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_VOLUME_CHANGED);
            filter.addAction(ACTION_STREAM_MUTE_CHANGED);
            // only the system sends these, so the receiver need not be exported
            ContextCompat.registerReceiver(this, volumeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            volumeReceiverRegistered = true;
        }
    }

    private void unwatchVolume() {
        if (volumeReceiverRegistered) {
            unregisterReceiver(volumeReceiver);
            volumeReceiverRegistered = false;
        }
    }

    private boolean isMusicMuted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            return true;
        }
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
    }
}
