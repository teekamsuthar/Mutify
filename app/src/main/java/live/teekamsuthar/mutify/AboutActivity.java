package live.teekamsuthar.mutify;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * About screen as in 2.6.2: logo card with the version; Links card (Contact Us, Rate, GitHub,
 * Telegram, Privacy Policy, Disclaimer, Open Source Licenses); credit to the author.
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdgeInsets.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        EdgeToEdgeInsets.apply(findViewById(R.id.appBar), findViewById(R.id.scrollView));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView version = findViewById(R.id.aboutVersion);
        version.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        findViewById(R.id.aboutContact).setOnClickListener(v -> ExternalLinks.sendFeedbackEmail(this));
        findViewById(R.id.aboutRate).setOnClickListener(v -> ExternalLinks.openPlayStore(this));
        findViewById(R.id.aboutGitHub).setOnClickListener(
                v -> ExternalLinks.openUrl(this, getString(R.string.url_github)));
        bindOptionalLink(R.id.aboutTelegram, R.string.url_telegram);
        bindOptionalLink(R.id.aboutPrivacyPolicy, R.string.url_privacy_policy);
        findViewById(R.id.aboutDisclaimer).setOnClickListener(
                v -> showTextDialog(R.string.about_disclaimer, R.string.about_disclaimer_text));
        findViewById(R.id.aboutLicenses).setOnClickListener(
                v -> showTextDialog(R.string.about_licenses, R.string.about_licenses_text));
        findViewById(R.id.aboutCredit).setOnClickListener(v -> showContributors());
    }

    // the credit opens the contributor list (as in 2.6.2); a tap on a name opens their GitHub profile
    private void showContributors() {
        String[] names = getResources().getStringArray(R.array.contributor_names);
        String[] handles = getResources().getStringArray(R.array.contributor_handles);
        String[] items = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            items[i] = names[i].equals(handles[i]) ? "@" + handles[i] : names[i] + " (@" + handles[i] + ")";
        }
        new MaterialAlertDialogBuilder(this)
                .setCustomTitle(getLayoutInflater().inflate(R.layout.dialog_contributors_title, null))
                .setItems(items, (dialog, which) ->
                        ExternalLinks.openUrl(this, "https://github.com/" + handles[which]))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // Telegram and Privacy Policy: hidden until their URL resource is filled in
    private void bindOptionalLink(int rowId, @StringRes int urlRes) {
        View row = findViewById(rowId);
        String url = getString(urlRes);
        if (AboutLinks.isShown(url)) {
            row.setVisibility(View.VISIBLE);
            row.setOnClickListener(v -> ExternalLinks.openUrl(this, url.trim()));
        } else {
            row.setVisibility(View.GONE);
        }
    }

    private void showTextDialog(@StringRes int title, @StringRes int text) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
