package live.teekamsuthar.mutify;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Opens Mutify's links outside the app: web pages, the Play Store listing and the contact email.
 * When no app can handle a link, a toast says so instead of crashing.
 */
final class ExternalLinks {

    private ExternalLinks() {
    }

    /** Opens a web page in the browser (or the app that owns the link, e.g. Telegram). */
    static void openUrl(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.link_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens Mutify in the Play Store app, or its Play Store web page when the app is missing. */
    static void openPlayStore(Context context) {
        String packageName = context.getPackageName();
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + packageName)));
        } catch (ActivityNotFoundException e) {
            openUrl(context, "https://play.google.com/store/apps/details?id=" + packageName);
        }
    }

    /** Lets the user pick an email app to write to the author, with the device info filled in. */
    static void sendFeedbackEmail(Context context) {
        try {
            context.startActivity(Intent.createChooser(Utils.getEmailIntent(),
                    context.getString(R.string.contact_us_chooser)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.link_no_email_app, Toast.LENGTH_SHORT).show();
        }
    }
}
