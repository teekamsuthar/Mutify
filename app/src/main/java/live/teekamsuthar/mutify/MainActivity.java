package live.teekamsuthar.mutify;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
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
import java.util.Locale;
import java.util.Random;

import static android.util.Log.i;
import static com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED;
import static com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE;
import static live.teekamsuthar.mutify.Utils.SPOTIFY_PACKAGE;
import static live.teekamsuthar.mutify.Utils.getTimeStamp;
import static live.teekamsuthar.mutify.Utils.getTimeStampFromDate;
import static live.teekamsuthar.mutify.Utils.openApp;

public class MainActivity extends AppCompatActivity implements ReceiverCallback {
    // global vars
    private static final String TAG = "MAIN_ACTIVITY";
    private ImageButton togglePlayPause;
    private LinearLayout mediaButtons;
    private MaterialSwitch adSwitch;
    private final CompoundButton.OnCheckedChangeListener adSwitchListener =
            (buttonView, isChecked) -> checkSwitch(isChecked);
    private MutifySettings settings;
    private StatusPublisher statusPublisher;
    private final StatusPublisher.Listener statusListener = this::renderStatus;
    private View tipsCardView;
    private SpotifySessionWatcher spotifySessionWatcher;
    private AudioManager audioManager;
    private TextView songInfoTextView, track, isPlaying, lastUpdated;
    /** In the main card: shown instead of the track while there is none to show. */
    private TextView stateLine;
    /** In the main card: an ad is being muted, or the media volume is muted. */
    private TextView muteIndicator;
    private MaterialButton muteButton;
    private View songInfoLayout;
    /** The status shown last; SETUP_NEEDED until the first render. */
    private MutifyStatus shownStatus = MutifyStatus.SETUP_NEEDED;
    /** Spotify reported a track since Mutify was last switched on. */
    private boolean hasSong;
    // volume keys change the media volume without telling the app: follow the stored volumes
    private final ContentObserver volumeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateMuteIndicator();
        }
    };
    private ImageView surpriseImageView;
    private AppUpdateManager appUpdateManager;
    private static final int MY_REQUEST_CODE = 17326;
    private static final String IN_APP_UPDATE = "InAppUpdateInfo";
    /** Preference: true until the first-launch onboarding was closed once. */
    private static final String KEY_FIRST_LAUNCH = "isFirstTimeLaunch";
    /** Saved state: the onboarding dialog was showing when the activity was recreated. */
    private static final String STATE_ONBOARDING_SHOWING = "onboardingShowing";
    /** Preference: the overflow menu's "Hide Tips" (illustration instead of the tips card). */
    private static final String KEY_HIDE_TIPS = "hide_tips";
    /** Preference: the overflow menu's "Auto-launch Spotify". */
    private static final String KEY_AUTO_LAUNCH_SPOTIFY = "auto-launch-spotify";
    private Onboarding onboarding;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdgeInsets.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeToEdgeInsets.apply(findViewById(R.id.appBar), findViewById(R.id.scrollView));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // muting on/off is one stored setting; the status shown is derived from it
        settings = MutifySettings.get(this);
        statusPublisher = StatusPublisher.get(this);
        // init switch
        adSwitch = findViewById(R.id.adSwitch);
        adSwitch.setOnCheckedChangeListener(adSwitchListener);
        // initialize all TextViews
        track = findViewById(R.id.track);
        songInfoTextView = findViewById(R.id.songInfoTextView);
        isPlaying = findViewById(R.id.isPlaying);
        lastUpdated = findViewById(R.id.lastUpdated);
        songInfoLayout = findViewById(R.id.songInfoLayout);
        // the title's gradient spans the title's width, so it follows every layout of it
        track.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applyTrackGradient());
        stateLine = findViewById(R.id.stateLine);
        stateLine.setOnClickListener(v -> onboarding.show());
        muteIndicator = findViewById(R.id.muteIndicator);
        muteButton = findViewById(R.id.mute);
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
        // Adjust Ads Volume card
        initAdVolumeCard();
        // tips card or illustration
        if (prefs.getBoolean(KEY_HIDE_TIPS, false)) {
            hideTips();
        } else {
            showTips();
        }
        // notification access onboarding: first launch, every start without access (Android's
        // auto-restore brings prefs back after a reinstall, but not the access), or opened from
        // the tile or widget
        onboarding = new Onboarding(this, this::onOnboardingClosed);
        if (savedInstanceState != null) {
            // recreated (e.g. theme switched): restore the dialog only if it was showing
            if (savedInstanceState.getBoolean(STATE_ONBOARDING_SHOWING)) {
                onboarding.show();
            }
        } else {
            boolean requested = consumeOnboardingRequest(getIntent());
            if (requested || Utils.getBooleanPreferenceValue(this, KEY_FIRST_LAUNCH)
                    || !SpotifyAdListenerService.hasNotificationAccess(this)) {
                onboarding.show();
            }
        }
        // check for update
        checkIfUpdateAvailable();
        // auto-launch Spotify: switch Mutify on and open Spotify, once per start of the screen
        // (not again when it is recreated, e.g. after a theme change); without notification access
        // only the onboarding shows, so Spotify does not cover it
        if (savedInstanceState == null && prefs.getBoolean(KEY_AUTO_LAUNCH_SPOTIFY, false)
                && isSpotifyInstalled() && SpotifyAdListenerService.hasNotificationAccess(this)) {
            adSwitch.setChecked(true);
            openSpotify();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // the tile or widget was tapped in Setup needed while this screen was open
        if (consumeOnboardingRequest(intent)) {
            onboarding.show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_ONBOARDING_SHOWING, onboarding.isShowing());
    }

    // true once for an intent from the tile or widget that asks for the onboarding dialog
    private static boolean consumeOnboardingRequest(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(Onboarding.EXTRA_SHOW_ONBOARDING, false)) {
            return false;
        }
        intent.removeExtra(Onboarding.EXTRA_SHOW_ONBOARDING);
        return true;
    }

    private void onOnboardingClosed(boolean setupDone) {
        MutifyStatus status = statusPublisher.refresh();
        renderStatus(status);
        if (setupDone) {
            maybeOfferTile(status);
        }
        if (Utils.getBooleanPreferenceValue(this, KEY_FIRST_LAUNCH)) {
            // first launch: the auto-start hint follows the onboarding, never on top of it
            Utils.setBooleanPreferenceValue(this, KEY_FIRST_LAUNCH, false);
            isChineseBrand();
        }
    }

    // Android 13+: offer the Quick Settings tile once, after the setup is done, never on top of
    // the onboarding dialog (closing it with access calls this again)
    private void maybeOfferTile(MutifyStatus status) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && status != MutifyStatus.SETUP_NEEDED && !onboarding.isShowing()) {
            MutifyTileService.offerToAddOnce(this);
        }
    }

    @Override
    public void metadataChanged(Song song) {
        // live update TextView when metadata changed
        track.setText(getString(R.string.now_playing_track, song.getTrack(), getTimeStamp(song.getLength())));
        songInfoTextView.setText(getString(R.string.now_playing_artist_album,
                song.getArtist(), song.getAlbum()));
        lastUpdated.setText(getString(R.string.now_playing_updated, getTimeStampFromDate(song.getTimeSent())));
        hasSong = true;
        renderNowPlaying();
        // log new song
        Log.i("New Song", "'" + song.getTrack() + "'"); // Log.i throws on a null message
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

    // the user flipped the switch: write the setting; the screen follows through renderStatus
    public void checkSwitch(boolean isChecked) {
        if (isChecked) {
            if (!SpotifyAdListenerService.hasNotificationAccess(this)) {
                // without notification access Mutify cannot see what Spotify plays
                renderStatus(statusPublisher.refresh()); // flicks the switch back to off
                onboarding.show();
                return;
            } else if (!isSpotifyInstalled()) {
                renderStatus(statusPublisher.refresh()); // flicks the switch back to off
                Toast.makeText(this, R.string.spotify_not_installed, Toast.LENGTH_LONG).show();
                return;
            }
        }
        // the listener service reacts at once; switching off during an ad unmutes right away
        settings.setEnabled(isChecked);
    }

    // shows the current status: switch, media buttons, now playing and the mute indicator
    private void renderStatus(MutifyStatus status) {
        shownStatus = status;
        boolean on = status.isOn();
        if (adSwitch.isChecked() != on) {
            // set the switch without treating it as a tap
            adSwitch.setOnCheckedChangeListener(null);
            adSwitch.setChecked(on);
            adSwitch.setOnCheckedChangeListener(adSwitchListener);
        }
        // show media buttons and the current track only while Mutify is on
        mediaButtons.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            spotifySessionWatcher.start();
        } else {
            spotifySessionWatcher.stop();
            // clear text-views
            hasSong = false;
            songInfoTextView.setText("");
            isPlaying.setText("");
            track.setText("");
            lastUpdated.setText("");
        }
        renderNowPlaying();
        // an ad was just muted or unmuted
        updateMuteIndicator();
    }

    // the track while Mutify is on and Spotify reported one; otherwise one line saying why not
    private void renderNowPlaying() {
        boolean showSong = shownStatus.isOn() && hasSong;
        songInfoLayout.setVisibility(showSong ? View.VISIBLE : View.GONE);
        stateLine.setVisibility(showSong ? View.GONE : View.VISIBLE);
        if (showSong) {
            return;
        }
        boolean setupNeeded = shownStatus == MutifyStatus.SETUP_NEEDED;
        @StringRes int text;
        if (setupNeeded) {
            text = R.string.main_state_setup_needed;
        } else if (shownStatus.isOn()) {
            text = R.string.main_state_waiting;
        } else {
            text = R.string.main_state_off;
        }
        stateLine.setText(text);
        // in Setup needed the line opens the onboarding, so it looks like a link
        stateLine.setClickable(setupNeeded);
        stateLine.setTextColor(MaterialColors.getColor(stateLine,
                setupNeeded ? androidx.appcompat.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    // 2.6.2's olive-blue-purple gradient across the track title
    private void applyTrackGradient() {
        int width = track.getWidth() - track.getTotalPaddingLeft() - track.getTotalPaddingRight();
        if (width <= 0) {
            return;
        }
        track.getPaint().setShader(new LinearGradient(0, 0, width, 0,
                new int[]{
                        ContextCompat.getColor(this, R.color.track_gradient_start),
                        ContextCompat.getColor(this, R.color.track_gradient_center),
                        ContextCompat.getColor(this, R.color.track_gradient_end)},
                null, Shader.TileMode.CLAMP));
        track.invalidate();
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
            Toast.makeText(this, R.string.spotify_not_installed, Toast.LENGTH_SHORT).show();
        }
    }

    // the muted indicator in the main card: an ad being muted (or played quieter), or the media
    // volume muted (Mute button, volume keys); hidden otherwise. Replaces 2.6.2's red navigation
    // bar, which Android 15+ no longer draws.
    private void updateMuteIndicator() {
        muteButton.setText(isDeviceMuted() ? R.string.unmute : R.string.mute);
        @StringRes int text;
        @AttrRes int background;
        @AttrRes int foreground;
        if (shownStatus == MutifyStatus.AD_MUTED) {
            text = settings.getAdVolumeStep() == AdVolumeSession.STEP_MUTE
                    ? R.string.mute_indicator_ad_muted : R.string.mute_indicator_ad_quieter;
            background = com.google.android.material.R.attr.colorPrimaryContainer;
            foreground = com.google.android.material.R.attr.colorOnPrimaryContainer;
        } else if (isDeviceMuted()) {
            text = R.string.mute_indicator_device_muted;
            background = com.google.android.material.R.attr.colorErrorContainer;
            foreground = com.google.android.material.R.attr.colorOnErrorContainer;
        } else {
            muteIndicator.setVisibility(View.GONE);
            return;
        }
        int foregroundColor = MaterialColors.getColor(muteIndicator, foreground);
        muteIndicator.setText(text);
        muteIndicator.setTextColor(foregroundColor);
        TextViewCompat.setCompoundDrawableTintList(muteIndicator, ColorStateList.valueOf(foregroundColor));
        ViewCompat.setBackgroundTintList(muteIndicator,
                ColorStateList.valueOf(MaterialColors.getColor(muteIndicator, background)));
        muteIndicator.setVisibility(View.VISIBLE);
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

    @Override
    protected void onStart() {
        super.onStart();
        // follow status changes (tile, widget, ads, access) while the screen is visible
        statusPublisher.addListener(statusListener);
        // the media volume can change while the screen is visible (volume keys)
        getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver);
    }

    @Override
    protected void onStop() {
        statusPublisher.removeListener(statusListener);
        getContentResolver().unregisterContentObserver(volumeObserver);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // insures correct play/pause drawable is shown at resume
        updatePlayPauseButton(audioManager.isMusicActive());
        // re-check everything (the muted indicator included), notification access included: the user may have changed it meanwhile
        MutifyStatus status = statusPublisher.refresh();
        renderStatus(status);
        // Android 13+: offer the Quick Settings tile once, after the setup is done
        maybeOfferTile(status);
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

    // No onBackPressed override: back uses the platform default (predictive back; since Android 12
    // a root activity is moved to the background instead of being finished).

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.toggleTips).setChecked(prefs.getBoolean(KEY_HIDE_TIPS, false));
        menu.findItem(R.id.autoLaunchSpotify).setChecked(prefs.getBoolean(KEY_AUTO_LAUNCH_SPOTIFY, false));
        menu.findItem(themeMenuItem(settings.getThemeMode())).setChecked(true);
        menu.findItem(languageMenuItem(AppLanguage.fromTags(
                AppCompatDelegate.getApplicationLocales().toLanguageTags()))).setChecked(true);
        return true;
    }

    private static int languageMenuItem(AppLanguage language) {
        switch (language) {
            case ENGLISH:
                return R.id.languageEnglish;
            case GERMAN:
                return R.id.languageGerman;
            case SPANISH:
                return R.id.languageSpanish;
            case FRENCH:
                return R.id.languageFrench;
            case PORTUGUESE_BRAZIL:
                return R.id.languagePortugueseBrazil;
            case VIETNAMESE:
                return R.id.languageVietnamese;
            default:
                return R.id.languageSystem;
        }
    }

    @Nullable
    private static AppLanguage languageForMenuItem(int id) {
        for (AppLanguage language : AppLanguage.values()) {
            if (languageMenuItem(language) == id) {
                return language;
            }
        }
        return null;
    }

    private static int themeMenuItem(ThemeMode mode) {
        switch (mode) {
            case LIGHT:
                return R.id.themeLight;
            case DARK:
                return R.id.themeDark;
            default:
                return R.id.themeSystem;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.support) {
            showSupportDialog();
            return true;
        }
        if (id == R.id.reportBug) {
            ExternalLinks.openUrl(this, getString(R.string.url_github_issues));
            return true;
        }
        if (id == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        if (id == R.id.themeSystem || id == R.id.themeLight || id == R.id.themeDark) {
            item.setChecked(true);
            // MutifyApp applies it app-wide; this screen is recreated in the new theme
            settings.setThemeMode(id == R.id.themeLight ? ThemeMode.LIGHT
                    : id == R.id.themeDark ? ThemeMode.DARK : ThemeMode.SYSTEM);
            return true;
        }
        AppLanguage language = languageForMenuItem(id);
        if (language != null) {
            item.setChecked(true);
            // AppCompat stores it (on Android 13+ as the system's per-app language) and recreates
            // the open screens in the new language
            AppCompatDelegate.setApplicationLocales(language == AppLanguage.SYSTEM
                    ? LocaleListCompat.getEmptyLocaleList()
                    : LocaleListCompat.forLanguageTags(language.tag));
            return true;
        }
        if (id == R.id.toggleTips) {
            item.setChecked(!item.isChecked());
            prefs.edit().putBoolean(KEY_HIDE_TIPS, item.isChecked()).apply();
            if (item.isChecked()) {
                hideTips();
            } else {
                showTips();
            }
            return true;
        }
        if (id == R.id.autoLaunchSpotify) {
            item.setChecked(!item.isChecked());
            prefs.edit().putBoolean(KEY_AUTO_LAUNCH_SPOTIFY, item.isChecked()).apply();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // toolbar heart: Buy Me a Coffee, with PayPal as the alternative
    private void showSupportDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.support_mutify)
                .setMessage(R.string.support_message)
                .setPositiveButton(R.string.support_buy_me_a_coffee,
                        (dialog, which) -> ExternalLinks.openUrl(this, getString(R.string.url_buy_me_a_coffee)))
                .setNeutralButton(R.string.support_paypal,
                        (dialog, which) -> ExternalLinks.openUrl(this, getString(R.string.url_paypal)))
                .show();
    }

    private void hideTips() {
        tipsCardView.setVisibility(View.GONE);
        surpriseImageView.setVisibility(View.VISIBLE);
    }

    private void showTips() {
        tipsCardView.setVisibility(View.VISIBLE);
        surpriseImageView.setVisibility(View.GONE);
    }

    // Adjust Ads Volume: a 6-step slider (mute, 10–50 %) stored in MutifySettings; the listener
    // service applies a new step to a running ad at once (the slider wins over the volume keys
    // during an ad) and to every later ad
    private void initAdVolumeCard() {
        Slider slider = findViewById(R.id.adVolumeSlider);
        TextView state = findViewById(R.id.adVolumeState);
        slider.setLabelFormatter(value -> adVolumeStepLabel(Math.round(value)));
        int step = settings.getAdVolumeStep();
        slider.setValue(step);
        state.setText(adVolumeStepDescription(step));
        slider.addOnChangeListener((s, value, fromUser) -> {
            int newStep = Math.round(value);
            state.setText(adVolumeStepDescription(newStep));
            if (fromUser) {
                settings.setAdVolumeStep(newStep);
                // during an ad the indicator says "muted" or "quieter" by the step
                updateMuteIndicator();
            }
        });
        findViewById(R.id.adVolumeHelp).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ad_volume_title)
                .setMessage(R.string.ad_volume_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show());
        findViewById(R.id.adVolumeWarning).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ad_volume_warning_title)
                .setMessage(R.string.ad_volume_warning)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    private String adVolumeStepLabel(int step) {
        return step == AdVolumeSession.STEP_MUTE
                ? getString(R.string.ad_volume_step_mute)
                : getString(R.string.ad_volume_step_percent, step * 10);
    }

    private String adVolumeStepDescription(int step) {
        return step == AdVolumeSession.STEP_MUTE
                ? getString(R.string.ad_volume_state_mute)
                : getString(R.string.ad_volume_state_percent, step * 10);
    }

    public void showSupportToast(View view) {
        Toast infoToast = new Toast(this);
        infoToast.setGravity(Gravity.CENTER, 0, 0);
        Toast.makeText(this, getString(R.string.warning_toast_message), Toast.LENGTH_LONG).show();
    }

    // one button: mutes the media volume, or unmutes it when it is muted (label follows the state)
    public void handleMuteUnmute(View view) {
        if (isDeviceMuted()) {
            unmute();
        } else {
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
            togglePlayPause.setContentDescription(getString(R.string.pause));
        } else {
            togglePlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_play_arrow));
            togglePlayPause.setContentDescription(getString(R.string.play));
        }
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
                Toast.makeText(this, R.string.update_install_failed, Toast.LENGTH_LONG).show();
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
        onboarding.dismissQuietly();
        // unregister listener for In app update
        appUpdateManager.unregisterListener(listener);
        spotifySessionWatcher.stop();
    }

    private void notifyUserToUpdate() {
        Snackbar snackbar =
                Snackbar.make(findViewById(android.R.id.content),
                        R.string.update_downloaded,
                        Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.update_restart, view -> appUpdateManager.completeUpdate());
        snackbar.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MY_REQUEST_CODE) {
            switch (resultCode) {
                case RESULT_OK:
                    Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_LONG).show();
                    Log.d(IN_APP_UPDATE, "Downloading update: " + resultCode);
                    break;
                case RESULT_CANCELED:
                    Toast.makeText(this, R.string.update_canceled, Toast.LENGTH_LONG).show();
                    Log.d(IN_APP_UPDATE, "Update Canceled: " + resultCode);
                    break;
                case RESULT_IN_APP_UPDATE_FAILED:
                    Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show();
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
        String device = Build.MANUFACTURER.toLowerCase(Locale.ROOT); // ROOT: a Turkish "I" must still match
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
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.auto_start_message)
                .setCancelable(false)
                .setPositiveButton(R.string.auto_start_continue, new DialogInterface.OnClickListener() {
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