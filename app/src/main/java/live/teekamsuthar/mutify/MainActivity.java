package live.teekamsuthar.mutify;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static live.teekamsuthar.mutify.Utils.getTimeStamp;
import static live.teekamsuthar.mutify.Utils.getTimeStampFromDate;
import static live.teekamsuthar.mutify.Utils.openApp;

public class MainActivity extends AppCompatActivity implements ReceiverCallback {

    //    private static Logger logger = Logger.getLogger("log: ");
//    private boolean isMuted;
//    private boolean isPlaying;
    private Intent loggerService;
    private SwitchMaterial adSwitch;
    private CardView cardView;
    private SpotifyBroadcastReceiver mBroadcastListener;
    private AudioManager audioManager;
    private TextView track, songInfoTextView, playing, muteTimer;
    private int playbackPosition;
    ScheduledExecutorService scheduledMuteExecutorService;
    ScheduledExecutorService scheduledUnMuteExecutorService;
    private ScheduledFuture<?> scheduledMute;
    private ScheduledFuture<?> scheduledUnMute;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // init switch
        adSwitch = findViewById(R.id.adSwitch);
        // initialize all TextViews
        track = findViewById(R.id.track);
        songInfoTextView = findViewById(R.id.songInfoTextView);
        playing = findViewById(R.id.isPlaying);
        muteTimer = findViewById(R.id.muteTimer);
        cardView = findViewById(R.id.cardView);
        // initialize audio manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // Create the broadcast listener and register the filters
        mBroadcastListener = new SpotifyBroadcastReceiver(this);
        // init shared prefs
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // check if Device is muted
        isDeviceMuted();
    }


    @Override
    public void metadataChanged(Song songFromIntent) {
        updateSongTextView(songFromIntent);
        // log new song
        System.out.println("New Song: " + songFromIntent.getTrack());
    }

    @Override
    public void playbackStateChanged(boolean playState, int pbPos, Song songFromIntent) {
        playbackPosition = pbPos;
//        isPlaying = playState;
        if (playState) {
            playing.setText(R.string.play_state_text);
        } else {
            playing.setText(R.string.last_detected_song);
        }
//        playbackPos.setText(String.format("Playback position: %s", getTimeStamp(pbPos)));
        handleNewSongIntent(playState, songFromIntent);
    }

    // triggered when a new song comes, or playback resumes
    private void handleNewSongIntent(boolean playState, Song songFromIntent) {
        // if existing mute timer, cancel it!
        cancelMuteTimer("handle new song: ");
        // mute timer when song starts playing!
        if (playState) {
            setMuteTimer(calculateTime(songFromIntent));
            // set a new 800ms unmute timer in case {already muted} and song is playing!
            if (isDeviceMuted()) {
                setUnmuteTimer();
            }
        } else {
            handleSongPaused();
        }
    }

    private long calculateTime(Song songFromIntent) {
        return songFromIntent.timeRemaining(playbackPosition) - songFromIntent.getPropagation();
    }

    // when user press pause button
    private void handleSongPaused() {
        System.out.println("Paused: not playing");
        cancelMuteTimer("song PAUSED: ");
    }

    private void updateSongTextView(Song song) {
        // live update TextView with received broadcast
        track.setText(String.format("%s (%s)", song.getTrack(), getTimeStamp(song.getLength())));
        songInfoTextView.setText(String.format("By %s\nFrom %s\nas last updated at -> %s",
                song.getArtist(), song.getAlbum(), getTimeStampFromDate(song.getTimeSent())));
    }

    private void cancelMuteTimer(String cause) {
        if (scheduledMute != null && !scheduledMute.isCancelled()) {
            if (scheduledMute.cancel(false)) {
                muteTimer.setText(R.string.mute_timer_cancelled);
                System.out.println(cause + "Mute timer cancelled");
            } else {
                System.out.println(cause + "Mute timer couldn't be cancelled");
            }
        }
    }

    private void setMuteTimer(final long waitTime) {
        // TODO what will be most appropriate size of core pool?
        // Create the scheduler & spawn a new thread pool every time
        scheduledMuteExecutorService = Executors.newScheduledThreadPool(1);
        // Create the task to execute
        final Runnable muteTask = () -> {
            try {
                mute();
                scheduledMute.get();
            } catch (InterruptedException e) { // handle exceptions
                System.out.println("Scheduled execution was interrupted(muteTask): \n" + e.getMessage());
            } catch (CancellationException e) {
                System.out.println("Scheduled thread has been cancelled(muteTask): \n" + e.getCause());
            } catch (ExecutionException e) {
                System.out.println("Uncaught exception in scheduled execution(muteTask): \n" + e.getCause());
            }
        };
        scheduledMute = scheduledMuteExecutorService.schedule(muteTask, waitTime, TimeUnit.MILLISECONDS);
        muteTimer.setText(String.format("Muting in: %s", getTimeStamp(waitTime)));
        System.out.println("muting in: " + getTimeStamp(waitTime));
    }

    private void setUnmuteTimer() {
        scheduledUnMuteExecutorService = Executors.newScheduledThreadPool(1);
        // Create the task to execute
        final Runnable unmuteTask = () -> {
            try {
                unmute();
                scheduledUnMute.get();
            } catch (InterruptedException e) { // handle exceptions
                System.out.println("Scheduled execution was interrupted(UNmuteTask): \n" + e.getMessage());
            } catch (CancellationException e) {
                System.out.println("Scheduled thread has been cancelled(UNmuteTask): \n" + e.getMessage());
            } catch (ExecutionException e) {
                System.out.println("Uncaught exception in scheduled execution(UNmuteTask): \n" + e.getCause());
            }
        };
        scheduledUnMute = scheduledUnMuteExecutorService.schedule(unmuteTask, 800, TimeUnit.MILLISECONDS);
        System.out.println("UNMUTE in: " + getTimeStamp(800));
    }


    private void mute() {
        // run only if not-muted already
        if (getMusicVolume() != 0) {
//            isMuted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
            muteTimer.setText(R.string.device_muted);
            System.out.println("MUTED!");
            cardView.setCardBackgroundColor(getResources().getColor(R.color.cardBackgroundPositive));
        }
    }

    private void unmute() {
//        isMuted = false; // set to not muted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } else {
            // TODO change index to original value
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
        System.out.println("UN-MUTED!");
        cardView.setCardBackgroundColor(getResources().getColor(R.color.cardBackgroundNegative));

    }

    public void checkSwitch(View view) {
        loggerService = new Intent(MainActivity.this, NotificationService.class);
        if (adSwitch.isChecked()) {
            startService();
        } else {
            stopService();
        }
    }

    private void startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(loggerService);
        } else {
            startService(loggerService);
        }
        // intent filters for broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED);
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED);

        registerReceiver(mBroadcastListener, filter);
        Toast.makeText(MainActivity.this, "Listening to Spotify...", Toast.LENGTH_SHORT).show();
    }

    private void stopService() {
        stopService(loggerService);
        try {
            unregisterReceiver(mBroadcastListener);
            // Time is up. Kill the executor service and its thread pool.
            scheduledMuteExecutorService.shutdown();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        cancelMuteTimer("service stopped");
        songInfoTextView.setText("");
        playing.setText("");
        muteTimer.setText("");
        Toast.makeText(MainActivity.this, "Service stopped...", Toast.LENGTH_SHORT).show();
    }

    private int getMusicVolume() {
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        System.out.println("Volume: " + volume);
        return volume;
    }

    private boolean isDeviceMuted() {
        return getMusicVolume() == 0;
    }

    public void openSpotify(View view) {
        openApp(getApplicationContext(), "com.spotify.music");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mBroadcastListener);
        } catch (Exception e) {             // already registered
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // set dark mode toggle
        menu.findItem(R.id.toggleDarkMode).setChecked(prefs.getBoolean("enabled", false));
        if (menu.findItem(R.id.toggleDarkMode).isChecked()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.play_store_link) {
            redirectToPlayStore();
            return true;
        }
        if (id == R.id.toggleDarkMode) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                prefs.edit().putBoolean("enabled", true).apply();
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                prefs.edit().putBoolean("enabled", false).apply();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void handleMuteUnmute(View view) {
        switch (view.getId()) {
            case R.id.unMute:
                unmute();
                break;
            case R.id.mute:
                mute();
                break;
        }
    }

    public void handleMedia(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.previous:
                if (audioManager.isMusicActive()) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    audioManager.dispatchMediaKeyEvent(event);
                }
                break;
            case R.id.pause:
                KeyEvent pause = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE);
                audioManager.dispatchMediaKeyEvent(pause);
                break;
            case R.id.play:
                KeyEvent play = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
                audioManager.dispatchMediaKeyEvent(play);
                break;
            case R.id.next:
                if (audioManager.isMusicActive()) {
                    KeyEvent next = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
                    audioManager.dispatchMediaKeyEvent(next);
                }
                break;
        }

    }

    private void redirectToPlayStore() {
        try {
            Uri updateUrl = Uri.parse("market://details?id=" + getPackageName());
            final Intent intent = new Intent(Intent.ACTION_VIEW, updateUrl);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong!", Toast.LENGTH_SHORT).show();
        }
    }

}