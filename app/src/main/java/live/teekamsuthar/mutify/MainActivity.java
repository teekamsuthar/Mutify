package live.teekamsuthar.mutify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static android.util.Log.i;
import static com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED;
import static com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE;
import static live.teekamsuthar.mutify.StopServiceBroadcastReceiver.shouldCloseService;
import static live.teekamsuthar.mutify.Utils.SPOTIFY_PACKAGE;
import static live.teekamsuthar.mutify.Utils.getEmailIntent;
import static live.teekamsuthar.mutify.Utils.getTimeStamp;
import static live.teekamsuthar.mutify.Utils.getTimeStampFromDate;
import static live.teekamsuthar.mutify.Utils.openApp;

public class MainActivity extends AppCompatActivity implements ReceiverCallback {
    // global vars
    private static final String TAG = "MAIN_ACTIVITY";
    private ImageButton togglePlayPause;
    private LinearLayout mediaButtons;
    private Intent notificationService;
    private static SwitchMaterial adSwitch;
    private CardView tipsCardView;
    private SpotifySessionWatcher spotifySessionWatcher;
    private AudioManager audioManager;
    private TextView songInfoTextView, track, isPlaying, lastUpdated;
    private ImageView surpriseImageView;
    private AppUpdateManager appUpdateManager;
    private static final int MY_REQUEST_CODE = 17326;
    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 17327;
    private static final String IN_APP_UPDATE = "InAppUpdateInfo";
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // init notification service intent
        notificationService = new Intent(MainActivity.this, NotificationService.class);
        // init switch
        adSwitch = findViewById(R.id.adSwitch);
        adSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> checkSwitch(isChecked));
        // initialize all TextViews
        track = findViewById(R.id.track);
        songInfoTextView = findViewById(R.id.songInfoTextView);
        isPlaying = findViewById(R.id.isPlaying);
        lastUpdated = findViewById(R.id.lastUpdated);
        tipsCardView = findViewById(R.id.cardView1);
        surpriseImageView = findViewById(R.id.imageView);
        surpriseImageView.setImageResource(getRandomImage());
        // ability to change images
        surpriseImageView.setOnClickListener(v -> surpriseImageView.setImageResource(getRandomImage()));
        togglePlayPause = findViewById(R.id.togglePlayPause);
        mediaButtons = findViewById(R.id.mediaButtons);
        mediaButtons.setVisibility(View.GONE);
        // init audio manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // follows Spotify's media session to show the current track
        spotifySessionWatcher = new SpotifySessionWatcher(this, this);
        // init shared prefs
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // check if Device currently muted or not
        updateMuteIndicator();
        // check for first time launch
        isFirstTimeLaunch();
        // check for update
        checkIfUpdateAvailable();
    }

    @Override
    public void metadataChanged(Song song) {
        // live update TextView when metadata changed
        track.setText(String.format("%s (%s)", song.getTrack(), getTimeStamp(song.getLength())));
        songInfoTextView.setText(String.format("By %s\nFrom %s",
                song.getArtist(), song.getAlbum()));
        lastUpdated.setText(String.format("(info updated @%s)", getTimeStampFromDate(song.getTimeSent())));
        // log new song
        Log.i("New Song", song.getTrack());
    }

    @Override
    public void playbackStateChanged(boolean playState, int playbackPos, Song song) {
        // display only: SpotifyAdListenerService does the muting, also while this activity is gone
        updatePlayPauseButton(playState);
        if (playState) {
            isPlaying.setText(R.string.play_state_text);
        } else {
            isPlaying.setText(R.string.last_detected_song);
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
            i("MUTED!", "device is muted");
            updateMuteIndicator();
        }
    }

    private void unmute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } else {
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
        i("UN-MUTED", "volume restored.");
        updateMuteIndicator();
        //  songCardView.setCardBackgroundColor(getResources().getColor(R.color.cardBackgroundNegative));
    }

    public void checkSwitch(boolean isChecked) {
        if (isChecked) {
            // make sure spotify is installed before starting the service
            if (!SpotifyAdListenerService.hasNotificationAccess(this)) {
                // without notification access Mutify cannot see what Spotify plays
                adSwitch.setChecked(false);
                showNotificationAccessDialog();
                return;
            } else if (isSpotifyInstalled()) {
                startService();
            } else {
                // if not installed, flick the switch back to off
                adSwitch.setChecked(!adSwitch.isChecked());
                // notify user // TODO: show a dialog instead
                Toast.makeText(this, "Spotify is not installed", Toast.LENGTH_LONG).show();
            }
            // show media buttons when service is on
            mediaButtons.setVisibility(View.VISIBLE);
        } else {
            stopService();
            // hide media buttons when service is off
            mediaButtons.setVisibility(View.GONE);
        }
    }

    private void startService() {
        // Android 13+: the "Mutify is running" notification needs the notification permission
        // (the muting itself works without it)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, POST_NOTIFICATIONS_REQUEST_CODE);
        }
        // create notification for Android above O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(notificationService);
        } else {
            startService(notificationService);
        }
        SpotifyAdListenerService.setMutingEnabled(this, true);
        spotifySessionWatcher.start();
    }

    private void stopService() {
        SpotifyAdListenerService.setMutingEnabled(this, false);
        spotifySessionWatcher.stop();
        stopService(notificationService); // stop foreground notification
        // clear text-views
        songInfoTextView.setText("");
        isPlaying.setText("");
        track.setText("");
        lastUpdated.setText("");
    }

    // returns device's music volume
    private int getMusicVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    // returns true if Music Vol is 0
    private boolean isDeviceMuted() {
        return getMusicVolume() == 0;
    }

    // open Spotify button
    public void handleOpenSpotify(View view) {
        // open Spotify
        openSpotify();
    }

    // attempt to open Spotify if already installed, else show error toast
    public void openSpotify() {
        if (isSpotifyInstalled()) {
            openApp(getApplicationContext(), SPOTIFY_PACKAGE);
        } else {
            Toast.makeText(this, "Could not find spotify!", Toast.LENGTH_SHORT).show();
        }
    }

    // returns true when notification service is running in the background
    private boolean isNotificationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (NotificationService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void updateMuteIndicator() {
        if (isDeviceMuted()) {
            // change nav-bar color to indicate the device muted.
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.Red));
        } else {
            // change nav-bar color to indicate device un-muted.
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        }
    }

    // returns a random image resource id from the ArrayList
    private static int getRandomImage() {
        List<Integer> items = new ArrayList<>();
        items.add(R.drawable.ic_music_re);
        items.add(R.drawable.ic_compose_music);
        items.add(R.drawable.ic_happy_music);
        items.add(R.drawable.ic_audio_player);
        items.add(R.drawable.ic_meditating);
        items.add(R.drawable.ic_listening);
        items.add(R.drawable.ic_reading);
        items.add(R.drawable.ic_walk_in_the_city);
        return items.get(new Random().nextInt(items.size()));
    }

    // check switch if service is already running & restore switch's "running" state when app bring to foreground.
    private void checkServiceState() {
        if (isNotificationServiceRunning() && !prefs.getBoolean("auto-launch-spotify", false) && !shouldCloseService) {
            adSwitch.setChecked(true);
            Log.d(TAG, "Turned switch on after activity was destroyed!");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // insures correct play/pause drawable is shown at resume
        updatePlayPauseButton(audioManager.isMusicActive());
        // check if the device currently muted or not
        updateMuteIndicator();
        // check if service is already running.
        checkServiceState();
        // prompt user if a new update has already downloaded and ready to be installed.
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                notifyUserToUpdate();
            }
        });
//        // when stop action triggered from notification.
//        if (shouldCloseService) {
//            shouldCloseService = false;
//            Toast.makeText(this, "Shutting down...", Toast.LENGTH_SHORT).show();
//            adSwitch.setChecked(false);
//            this.finishAndRemoveTask();
//        }
    }

    public static void closeSwitch() {
        if (adSwitch != null) adSwitch.setChecked(false);
        shouldCloseService = false;
    }


    @Override
    public void onBackPressed() {
        // to send app in background when back key pressed
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
        // show/hide tips toggle
        menu.findItem(R.id.toggleTips).setChecked(prefs.getBoolean("hide_tips", false));
        if (menu.findItem(R.id.toggleTips).isChecked()) {
            hideTips();
        } else {
            showTips();
        }
        // auto-launch spotify
        menu.findItem(R.id.autoLaunchSpotify).setChecked(prefs.getBoolean("auto-launch-spotify", false));
        if (menu.findItem(R.id.autoLaunchSpotify).isChecked()) {
            if (isSpotifyInstalled()) {
                // start service and then launch Spotify
                adSwitch.setChecked(true);
                openSpotify();
            }
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
        if (id == R.id.toggleTips) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) {
                prefs.edit().putBoolean("hide_tips", true).apply();
                hideTips();
            } else {
                prefs.edit().putBoolean("hide_tips", false).apply();
                showTips();
            }
            return true;
        }
        if (id == R.id.autoLaunchSpotify) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) {
                prefs.edit().putBoolean("auto-launch-spotify", true).apply();
            } else {
                prefs.edit().putBoolean("auto-launch-spotify", false).apply();
            }
            return true;
        }
        if (id == R.id.contact_us) {
            /* get email Intent */
            Intent sendEmail = getEmailIntent();
            /* Send it off to the Activity-Chooser */
            startActivity(Intent.createChooser(sendEmail, "Choose an email app to send your feedback!"));
        }
        return super.onOptionsItemSelected(item);
    }

    private void hideTips() {
        tipsCardView.setVisibility(View.GONE);
        surpriseImageView.setVisibility(View.VISIBLE);
    }

    private void showTips() {
        tipsCardView.setVisibility(View.VISIBLE);
        surpriseImageView.setVisibility(View.GONE);
    }

    public void showSupportToast(View view) {
        Toast infoToast = new Toast(this);
        infoToast.setGravity(Gravity.CENTER, 0, 0);
        Toast.makeText(this, getString(R.string.warning_toast_message), Toast.LENGTH_LONG).show();
    }

    public void handleMuteUnmute(View view) {
        int id = view.getId();
        // handle taps on mute/unMute buttons
        if (id == R.id.unMute) {
            unmute();
        } else if (id == R.id.mute) {
            mute();
        }
    }

    public void handleMedia(View view) {
        int id = view.getId();
        // replaced switch statements with if-else because https://stackoverflow.com/q/63290845/11141100
        if (id == R.id.previous) {
            if (audioManager.isMusicActive()) {
                KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                audioManager.dispatchMediaKeyEvent(event);
            }
        } else if (id == R.id.togglePlayPause) {// get music playing info
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
        } else if (id == R.id.next) {
            if (audioManager.isMusicActive()) {
                KeyEvent next = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
                audioManager.dispatchMediaKeyEvent(next);
            }
        }
    }

    // updates the drawable for play/pause button
    private void updatePlayPauseButton(boolean isMusicActive) {
        if (isMusicActive) {
            togglePlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause));
        } else {
            togglePlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_play_arrow));
        }
    }

    // checks if the app has started for the first time
    private void isFirstTimeLaunch() {
        if (Utils.getBooleanPreferenceValue(this, "isFirstTimeLaunch")) {
            showAlertDialog();
            Log.d(TAG, "First time Launch");
        }
    }

    // first launch: explain and ask for notification access, then the auto-start hint
    private void showAlertDialog() {
        showNotificationAccessDialog(() -> {
            Utils.setBooleanPreferenceValue(this, "isFirstTimeLaunch", false);
            isChineseBrand();
        });
    }

    private void showNotificationAccessDialog() {
        showNotificationAccessDialog(null);
    }

    // Mutify reads Spotify's media session, which Android only allows with notification access.
    private void showNotificationAccessDialog(@Nullable Runnable onDismiss) {
        if (SpotifyAdListenerService.hasNotificationAccess(this)) {
            if (onDismiss != null) onDismiss.run();
            return;
        }
        TextView titleView = new TextView(this);
        titleView.setText(getString(R.string.dialog_title));
        titleView.setTextSize(20.0f);
        titleView.setPadding(15, 20, 15, 20);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        TextView messageView = new TextView(this);
        messageView.setText(getString(R.string.dialog_message));
        messageView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        messageView.setTextSize(16.0f);
        messageView.setPadding(30, 0, 30, 0);
        new AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setView(messageView)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_positive_btn, (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, "Open Settings > Notifications > Notification access and allow Mutify", Toast.LENGTH_LONG).show();
                    }
                    if (onDismiss != null) onDismiss.run();
                })
                .setNegativeButton(R.string.dialog_negative_btn, (dialog, which) -> {
                    if (onDismiss != null) onDismiss.run();
                })
                .show();
    }

    // returns true if Spotify installed on device
    private boolean isSpotifyInstalled() {
        final PackageManager packageManager = this.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE);
        if (intent == null) {
            return false;
        }
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    // open Play Store app for rate & review
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
//                    // The flow has finished. The API does not indicate whether the user.
//                    // reviewed or not, or even whether the review dialog was shown. Thus, no
//                    // matter the result, we continue our app flow.
//                });
//            } else {
//                // There was some problem, continue regardless of the result.
//            }
//        });
    }

    // In-app update flexible workflow https://stackoverflow.com/a/57221648/11141100
    private void checkIfUpdateAvailable() {
        appUpdateManager = AppUpdateManagerFactory.create(this);
        appUpdateManager.registerListener(listener);
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(FLEXIBLE)) {
                requestUpdate(appUpdateInfo);
                i(IN_APP_UPDATE, "Update is Available");
            } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE) {
                i(IN_APP_UPDATE, "Update isn't Available");
            } else {
                i(IN_APP_UPDATE, "Something went wrong!");
            }
            i(IN_APP_UPDATE, "packageName: " + appUpdateInfo.packageName()
                    + "|" + "availableVersionCode: " + appUpdateInfo.availableVersionCode()
                    + "|" + "updateAvailability: " + appUpdateInfo.updateAvailability()
                    + "|" + "installStatus: " + appUpdateInfo.installStatus());
        });
    }

    private void requestUpdate(AppUpdateInfo appUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.FLEXIBLE, MainActivity.this, MY_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SwitchIntDef")
    final InstallStateUpdatedListener listener = installState -> {
        final int installStatus = installState.installStatus();
        switch (installStatus) {
            case InstallStatus.DOWNLOADED:
                notifyUserToUpdate(); // notify user to install downloaded update
                Log.d(IN_APP_UPDATE, "Update Downloaded");
                break;
            case InstallStatus.FAILED:
                Toast.makeText(this, "Failed to Install an Update for Mutify…", Toast.LENGTH_LONG).show();
                Log.d(IN_APP_UPDATE, "Failed");
                break;
            // for logging purpose only
            case InstallStatus.INSTALLED:
                Log.d(IN_APP_UPDATE, "Installed");
                break;
            default:
                Log.d(IN_APP_UPDATE, "Pending…");
                break;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // unregister listener for In app update
        appUpdateManager.unregisterListener(listener);
        spotifySessionWatcher.stop();
    }

    private void notifyUserToUpdate() {
        Snackbar snackbar =
                Snackbar.make(findViewById(android.R.id.content),
                        "An update has just been downloaded… Restart?",
                        Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("RESTART", view -> appUpdateManager.completeUpdate());
        snackbar.setActionTextColor(getResources().getColor(R.color.colorPrimary));
        snackbar.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MY_REQUEST_CODE) {
            switch (resultCode) {
                case RESULT_OK:
                    Toast.makeText(this, "Downloading update…", Toast.LENGTH_LONG).show();
                    Log.d(IN_APP_UPDATE, "Downloading update: " + resultCode);
                    break;
                case RESULT_CANCELED:
                    Toast.makeText(this, "Update Canceled…", Toast.LENGTH_LONG).show();
                    Log.d(IN_APP_UPDATE, "Update Canceled: " + resultCode);
                    break;
                case RESULT_IN_APP_UPDATE_FAILED:
                    Toast.makeText(this, "Update Failed…", Toast.LENGTH_LONG).show();
                    Log.d(IN_APP_UPDATE, "Update Failed: " + resultCode);
                    break;
                default:
                    Log.d(IN_APP_UPDATE, "Something went wrong: " + resultCode);
                    break;
            }
        }
    }

    /* check if the device is chinese brand
        if yes, then show up a dialog which will navigate user to the auto start settings in his phone
        where he/she can mark this app to run in the background (because chinese brand device don't let apps
        to run foreground notification in the background until user manually give permission for it)
     */
    private void isChineseBrand(){
        String device = Build.MANUFACTURER.toLowerCase();
        Intent intent;
        switch (device){
            case "xiaomi":
                intent =  new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                break;
            case "miui":
                intent =  new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                break;
            case "huawei":
                intent = new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
                break;
            case "oppo":
                intent = new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"));
                break;
            case "vivo":
                intent = new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                break;
            case "oneplus":
                intent = new Intent().setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                break;
            case "asus":
                intent = new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).setData(
                        Uri.parse("mobilemanager://function/entry/AutoStart"));
                break;
            case "samsung":
                intent = new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"));
                break;
            default:
                intent = null;
        }

        if(intent != null) showAutoStartPermissionDialog(intent);
    }

    private void showAutoStartPermissionDialog(Intent intent){
        new AlertDialog.Builder(this)
                .setMessage("Give this app the auto start permission in order to work in the background")
                .setCancelable(false)
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try{
                            startActivity(intent);
                        }catch(Exception e){
                            Log.e("MainActivity" , e.toString());
                        }
                    }
                })
                .show();
    }
}