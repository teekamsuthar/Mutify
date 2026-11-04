package live.teekamsuthar.mutify;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The onboarding dialog for notification access, which Mutify needs to see what Spotify plays.
 * <p>
 * "Open settings" goes to Mutify's own access page where Android supports it (see
 * {@link OnboardingRules}); "Done" closes the dialog only once access is granted and says so
 * otherwise; "Later" closes it as it is. Sideloaded installs get a hint about Android 13's
 * "Allow restricted settings".
 */
final class Onboarding {

    /** Intent extra: open the main screen straight into this dialog (tile and widget). */
    static final String EXTRA_SHOW_ONBOARDING = "live.teekamsuthar.mutify.extra.SHOW_ONBOARDING";
    private static final String TAG = "Onboarding";

    /** Told when the dialog closes; {@code setupDone} is true when it closed with access granted. */
    interface Callback {
        void onOnboardingClosed(boolean setupDone);
    }

    private final Activity activity;
    private final Callback callback;
    @Nullable
    private AlertDialog dialog;

    Onboarding(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    /**
     * Opens the main screen with the onboarding dialog showing; used by the tile and the widget
     * while setup is needed. Brings an open main screen to the front (onNewIntent) instead of
     * stacking a second one.
     */
    static Intent openAppIntent(@NonNull Context context) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_SHOW_ONBOARDING, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    /**
     * Shows the dialog unless it is already showing. With access already granted there is nothing
     * to set up: the callback is told so at once.
     */
    @SuppressLint("InflateParams") // a dialog's content view has no parent to inflate into
    void show() {
        if (isShowing()) {
            return;
        }
        if (SpotifyAdListenerService.hasNotificationAccess(activity)) {
            callback.onOnboardingClosed(true);
            return;
        }
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_onboarding, null);
        View accessMissing = content.findViewById(R.id.onboardingAccessMissing);
        content.findViewById(R.id.onboardingOpenSettings).setOnClickListener(v -> {
            accessMissing.setVisibility(View.GONE);
            openNotificationAccessSettings();
        });
        if (OnboardingRules.showsRestrictedSettingsHint(Build.VERSION.SDK_INT, installerPackage())) {
            content.findViewById(R.id.onboardingRestrictedHint).setVisibility(View.VISIBLE);
            content.findViewById(R.id.onboardingOpenAppInfo).setOnClickListener(v -> openAppInfo());
        }
        AlertDialog shown = new MaterialAlertDialogBuilder(activity)
                .setView(content)
                // a stray tap outside must not lose the explanation; back and "Later" close it
                .setCancelable(true)
                // the click listener is replaced below so that "Done" can keep the dialog open
                .setPositiveButton(R.string.onboarding_done, null)
                .setNegativeButton(R.string.onboarding_later, null)
                .create();
        shown.setCanceledOnTouchOutside(false);
        shown.setOnDismissListener(d -> {
            if (dialog == d) {
                dialog = null;
                callback.onOnboardingClosed(SpotifyAdListenerService.hasNotificationAccess(activity));
            }
        });
        dialog = shown;
        shown.show();
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (SpotifyAdListenerService.hasNotificationAccess(activity)) {
                shown.dismiss();
            } else {
                accessMissing.setVisibility(View.VISIBLE);
                // the message sits below the fold on most phones: scroll it into view
                content.post(() -> ((ScrollView) content).smoothScrollTo(0, accessMissing.getBottom()));
            }
        });
    }

    /** Closes the dialog without telling the callback, e.g. when the activity goes away. */
    void dismissQuietly() {
        AlertDialog shown = dialog;
        dialog = null;
        if (shown != null) {
            shown.dismiss();
        }
    }

    // Android 11+: Mutify's own access page; before that, or if the page is missing, the list
    private void openNotificationAccessSettings() {
        if (OnboardingRules.opensOwnAccessPage(Build.VERSION.SDK_INT) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            SpotifyAdListenerService.componentName(activity).flattenToString());
            if (tryStart(detail)) {
                return;
            }
        }
        if (!tryStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            Toast.makeText(activity, R.string.onboarding_settings_not_found, Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo() {
        Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.getPackageName(), null));
        if (!tryStart(appInfo)) {
            tryStart(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean tryStart(Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "could not open " + intent.getAction(), e);
            return false;
        }
    }

    /** Who installed Mutify: e.g. the Play Store's package, or null for adb and unknown sources. */
    @Nullable
    @SuppressWarnings("deprecation")
    private String installerPackage() {
        PackageManager pm = activity.getPackageManager();
        String self = activity.getPackageName();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return pm.getInstallSourceInfo(self).getInstallingPackageName();
            }
            return pm.getInstallerPackageName(self);
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }
}
