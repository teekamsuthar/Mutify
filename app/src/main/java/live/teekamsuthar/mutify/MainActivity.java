package live.teekamsuthar.mutify;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static live.teekamsuthar.mutify.Utils.SPOTIFY_PACKAGE;
import static live.teekamsuthar.mutify.Utils.getEmailIntent;
import static live.teekamsuthar.mutify.Utils.getTimeStamp;
import static live.teekamsuthar.mutify.Utils.getTimeStampFromDate;
import static live.teekamsuthar.mutify.Utils.openApp;

public class MainActivity extends AppCompatActivity implements ReceiverCallback {

    private static final String TAG = "MAIN_ACTIVITY";
    private ImageButton togglePlayPause;
    private Intent notificationService;
    private SwitchMaterial adSwitch;
    private CardView songCardView, tipsCardView;
    private SpotifyBroadcastReceiver spotifyBroadcastReceiver;
    private AudioManager audioManager;
    private TextView songInfoTextView, track, isPlaying;
    private int playbackPosition;
    private ScheduledFuture<?> scheduledMute, scheduledUnMute;
    ScheduledExecutorService scheduledMuteExecutorService, scheduledUnMuteExecutorService;
    SharedPreferences prefs;
//    private boolean isMuted;
//    private boolean isPlaying = false;

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
        isPlaying = findViewById(R.id.isPlaying);
        songCardView = findViewById(R.id.cardView);
        tipsCardView = findViewById(R.id.cardView1);
        togglePlayPause = findViewById(R.id.togglePlayPause);
        // init audio manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // init the broadcast listener with callback
        spotifyBroadcastReceiver = new SpotifyBroadcastReceiver(this);
        // init shared prefs
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // check if Device is muted
        isDeviceMuted();
        // check for first time launch
        isFirstTimeLaunch();
    }

    @Override
    public void metadataChanged(Song song) {
        // live update TextView when metadata changed
        track.setText(String.format("%s (%s)", song.getTrack(), getTimeStamp(song.getLength())));
        songInfoTextView.setText(String.format("By %s\nFrom %s\nas last updated at -> %s",
                song.getArtist(), song.getAlbum(), getTimeStampFromDate(song.getTimeSent())));
        // log new song
        Log.i("New Song", song.getTrack());
    }

    @Override
    public void playbackStateChanged(boolean playState, int playbackPos, Song song) {
        playbackPosition = playbackPos;
        updatePlayPauseButton(playState);
        if (playState) {
            isPlaying.setText(R.string.play_state_text);
        } else {
            isPlaying.setText(R.string.last_detected_song);
        }
        handleNewSongIntent(playState, song);
    }

    // triggered whenever playback state changes
    private void handleNewSongIntent(boolean playState, Song song) {
        // if existing mute timer, cancel it!
        cancelMuteTimer("handle new song: ");
        // handling timers according to playback state!
        if (playState) {
            // calculate delay
            long delay = calculateTime(song);
            setMuteTimer(delay);
            // set a new 800ms unmute timer in case delay is positive and song starts playing!
            if (delay > 0) {
                setUnmuteTimer();
            }
        } else {
            cancelMuteTimer("song PAUSED: ");
            Log.v("Paused", "cancelling mute timer...");
        }
    }

    private long calculateTime(Song songFromIntent) {
        return songFromIntent.timeRemaining(playbackPosition) - songFromIntent.getElapsedTime();
    }


    private void cancelMuteTimer(String cause) {
        if (scheduledMute != null && !scheduledMute.isCancelled()) {
            if (scheduledMute.cancel(false)) {
//                muteTimer.setText(R.string.mute_timer_cancelled);
                Log.v(cause, "Mute timer cancelled...");
            } else {
                Log.w(cause, "Mute timer couldn't be cancelled...");
            }
        } else {
            Log.d(cause, "No timer is set");
        }
    }

    private void setMuteTimer(final long waitTime) {
        // Create the scheduler & spawn a new thread pool with each timer TODO figure what will be most appropriate size of core pool?
        scheduledMuteExecutorService = Executors.newScheduledThreadPool(1);
        // Create the task to execute
        final Runnable muteTask = () -> {
            try {
                mute();
                // https://stackoverflow.com/a/48181646/11141100 TODO verify if app freezing after some time is fixed.
                if (scheduledMute.isDone()) {
                    scheduledMute.get(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException | CancellationException | ExecutionException | TimeoutException e) { // handle exceptions
                Throwable cause = e.getCause();
                Log.e("Exception", "Scheduled execution was interrupted/cancelled (muteTask):", cause);
            }
        };
        scheduledMute = scheduledMuteExecutorService.schedule(muteTask, waitTime, TimeUnit.MILLISECONDS);
        // TODO should I put scheduledMute.shutdown() here?
//        muteTimer.setText(String.format("Muting in: %s", getTimeStamp(waitTime)));
        Log.i("Muting in:", getTimeStamp(waitTime));
    }

    private void setUnmuteTimer() {
        if (isDeviceMuted()) {
            scheduledUnMuteExecutorService = Executors.newScheduledThreadPool(1);
            // Create the task to execute
            final Runnable unmuteTask = () -> {
                try {
                    unmute();
                    if (scheduledUnMute.isDone()) {
                        scheduledUnMute.get(5, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException | CancellationException | ExecutionException | TimeoutException e) { // handle exceptions
                    Throwable cause = e.getCause();
                    Log.e("Exception", "Scheduled execution was interrupted/cancelled (Un-muteTask):", cause);
                }
            };
            scheduledUnMute = scheduledUnMuteExecutorService.schedule(unmuteTask, 800, TimeUnit.MILLISECONDS);
            Log.i("UNMUTE in:", getTimeStamp(800));
        }
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
//            muteTimer.setText(R.string.device_muted);
            Log.i("MUTED!", "device is muted");
            songCardView.setCardBackgroundColor(getResources().getColor(R.color.cardBackgroundPositive));
        }
    }

    private void unmute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } else {
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
        Log.i("UN-MUTED", "volume restored.");
        songCardView.setCardBackgroundColor(getResources().getColor(R.color.cardBackgroundNegative));
    }

    public void checkSwitch(View view) {
        notificationService = new Intent(MainActivity.this, NotificationService.class);
        if (adSwitch.isChecked()) {
            // make sure spotify is installed before starting the service
            if (isSpotifyInstalled()) {
                startService();
            } else {
                // if not installed, flick the switch back to off
                adSwitch.setChecked(!adSwitch.isChecked());
                // notify user // TODO: show a dialog instead
                Toast.makeText(this, "Spotify is not installed", Toast.LENGTH_SHORT).show();
            }
        } else {
            stopService();
        }
    }

    private void startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(notificationService);
        } else {
            startService(notificationService);
        }
        // intent filters for broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED);
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED);

        registerReceiver(spotifyBroadcastReceiver, filter);
        Toast.makeText(MainActivity.this, "Enjoy your ad-free music ;)", Toast.LENGTH_SHORT).show();
    }

    private void stopService() {
        try {
            unregisterReceiver(spotifyBroadcastReceiver);
            // Time is up. Kill the executor service and its thread pool.
//            if (scheduledUnMuteExecutorService != null && scheduledMuteExecutorService != null) {
//                scheduledMuteExecutorService.shutdown();
//                scheduledUnMuteExecutorService.shutdown();
//                Log.i("Stop Service", "Executor service is shutdown");
//            } else {
//                Log.d("Stop Service", "null");
//            }
        } catch (Exception e) {
            // WTF!??
            Log.wtf("error while stopping", e.getMessage());
        }
        stopService(notificationService); // stop logging service
        cancelMuteTimer("service stopped");
        songInfoTextView.setText("");
        isPlaying.setText("");
        track.setText("");
//        muteTimer.setText("");
        Toast.makeText(MainActivity.this, "Service stopped...", Toast.LENGTH_SHORT).show();
    }

    private int getMusicVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    private boolean isDeviceMuted() {
        return getMusicVolume() == 0;
    }

    public void openSpotify(View view) {
        if (isSpotifyInstalled()) {
            openApp(getApplicationContext(), SPOTIFY_PACKAGE);
        } else {
            Toast.makeText(this, "Could not find spotify!", Toast.LENGTH_SHORT).show();
        }
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        System.out.println("ON DESTROY");
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        System.out.println("ON PAUSE");
//    }

    @Override
    protected void onResume() {
        super.onResume();
        // insures correct play/pause drawable is shown at resume
        updatePlayPauseButton(audioManager.isMusicActive());
    }

    @Override
    public void onBackPressed() {
        // to send app in background when back key is pressed
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
        // turn off switch when UI change.
        resetSwitchWhenUiChange();
        // show/hide tips toggle
        menu.findItem(R.id.toggleTips).setChecked(prefs.getBoolean("hide_tips", false));
        if (menu.findItem(R.id.toggleTips).isChecked()) {
            tipsCardView.setVisibility(View.GONE);
        } else {
            tipsCardView.setVisibility(View.VISIBLE);
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
            resetSwitchWhenUiChange();
            if (item.isChecked()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                prefs.edit().putBoolean("enabled", true).apply();
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                prefs.edit().putBoolean("enabled", false).apply();
            }
            return true;
        }
        if (id == R.id.toggleTips) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) {
                prefs.edit().putBoolean("hide_tips", true).apply();
                tipsCardView.setVisibility(View.GONE);
            } else {
                prefs.edit().putBoolean("hide_tips", false).apply();
                tipsCardView.setVisibility(View.VISIBLE);
            }
            return true;
        }
        if (id == R.id.contact_us) {
            /* get email Intent */
            Intent sendEmail = getEmailIntent();
            /* Send it off to the Activity-Chooser */
            startActivity(Intent.createChooser(sendEmail, "Choose an email client to send your feedback!"));
        }
        return super.onOptionsItemSelected(item);
    }

    private void resetSwitchWhenUiChange() {
        if (adSwitch.isChecked()) {
            adSwitch.setChecked(false); // uncheck switch after UI change
        }
    }

    public void showSupportToast(View view) {
        Toast infoToast = new Toast(this);
        infoToast.setGravity(Gravity.CENTER, 0, 0);
        Toast.makeText(this, getString(R.string.warning_toast_message), Toast.LENGTH_LONG).show();
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
            case R.id.togglePlayPause:
                // get music playing info
                boolean isMusicActive = audioManager.isMusicActive();
                // send play/pause broadcast
                if (isMusicActive) {
                    KeyEvent pause = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE);
                    audioManager.dispatchMediaKeyEvent(pause);
                    updatePlayPauseButton(false);
                } else {
                    KeyEvent play = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
                    audioManager.dispatchMediaKeyEvent(play);
                    updatePlayPauseButton(true);
                }
                break;
            case R.id.next:
                if (audioManager.isMusicActive()) {
                    KeyEvent next = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
                    audioManager.dispatchMediaKeyEvent(next);
                }
                break;
        }
    }

    private void updatePlayPauseButton(boolean isMusicActive) {
        if (isMusicActive) {
            togglePlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause));
        } else {
            togglePlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_play_arrow));
        }
    }

    private void isFirstTimeLaunch() {
        if (Utils.getBooleanPreferenceValue(this, "isFirstTimeLaunch")) {
            showAlertDialog();
            Log.d(TAG, "First time Launch");
        }
    }

    private void showAlertDialog() {
        // defining custom text views
        TextView titleView = new TextView(this);
        titleView.setText(getString(R.string.dialog_title));
        titleView.setTextSize(20.0f);
        titleView.setPadding(15, 20, 15, 20);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        TextView messageView = new TextView(this);
        messageView.setText(getString(R.string.dialog_message));
        messageView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        messageView.setTextSize(16.0f);
        // setting views to dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCustomTitle(titleView);
        builder.setView(messageView);
        builder.setCancelable(false);
        // Do nothing inside click listeners because we override this button later to change the close behaviour.
        builder.setPositiveButton(R.string.dialog_positive_btn, (dialog, which) -> {
        });
        builder.setNegativeButton(R.string.dialog_negative_btn, (dialog, which) -> {
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        // Overriding the handler immediately after show (to prevent dialog closing after clicking the button).
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        positive.setOnClickListener(v -> {
            if (isSpotifyInstalled()) {
                // open Spotify settings on api above v24.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Intent spotifySettings = new Intent("android.intent.action.APPLICATION_PREFERENCES").setPackage(SPOTIFY_PACKAGE);
                    startActivity(spotifySettings);
                } else {
                    openApp(this, SPOTIFY_PACKAGE);
                }
            } else {
                Toast.makeText(this, "Couldn't find Spotify installed!", Toast.LENGTH_SHORT).show();
            }
        });
        negative.setOnClickListener(v -> {
            Utils.setBooleanPreferenceValue(this, "isFirstTimeLaunch", false); // set boolean then dismiss
            dialog.dismiss();
        });
    }

    private boolean isSpotifyInstalled() {
        final PackageManager packageManager = this.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE);
        if (intent == null) {
            return false;
        }
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    private void redirectToPlayStore() {
        try {
            Uri updateUrl = Uri.parse("market://details?id=" + getPackageName());
            final Intent intent = new Intent(Intent.ACTION_VIEW, updateUrl);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_NO_HISTORY |
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong!", Toast.LENGTH_SHORT).show();
        }

        // TODO: implement in-app review in later versions of Mutify
//      // ReviewManager manager = new FakeReviewManager(this); // to fake the behaviour
//      // ReviewManager manager = ReviewManagerFactory.create(this);
//        Task<ReviewInfo> request = manager.requestReviewFlow();
//        request.addOnCompleteListener(task -> {
//            if (task.isSuccessful()) {
//                // We can get the ReviewInfo object
//                ReviewInfo reviewInfo = task.getResult();
//                Task<Void> flow = manager.launchReviewFlow(this, reviewInfo);
//                flow.addOnCompleteListener(task2 -> {
//                    // The flow has finished. The API does not indicate whether the user
//                    // reviewed or not, or even whether the review dialog was shown. Thus, no
//                    // matter the result, we continue our app flow.
//                });
//            } else {
//                // There was some problem, continue regardless of the result.
//            }
//        });
    }
}