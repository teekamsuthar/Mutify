package live.teekamsuthar.mutify;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * What the home-screen widget shows for a {@link MutifyStatus}, and what a tap on it does. Kept
 * apart from {@link MutifyWidgetProvider} so the mapping can be unit-tested on the JVM.
 */
final class WidgetContent {

    /**
     * From this minimum width on (a 2x1 widget is 2 x 70 - 30 = 110dp, less some launcher padding)
     * the label goes beside the icon instead of below it.
     */
    static final int WIDE_MIN_WIDTH_DP = 100;

    @StringRes
    final int label;
    @DrawableRes
    final int icon;
    @DrawableRes
    final int background;
    @ColorRes
    final int labelColor;
    /** A tap opens the app (setup needed) instead of switching muting on or off. */
    final boolean tapOpensApp;

    private WidgetContent(@StringRes int label, @DrawableRes int icon, @DrawableRes int background,
                          @ColorRes int labelColor, boolean tapOpensApp) {
        this.label = label;
        this.icon = icon;
        this.background = background;
        this.labelColor = labelColor;
        this.tapOpensApp = tapOpensApp;
    }

    static WidgetContent of(MutifyStatus status) {
        switch (status) {
            case SETUP_NEEDED:
                return new WidgetContent(R.string.status_setup_needed, R.drawable.ic_widget_mutify_off,
                        R.drawable.widget_background_off, R.color.widget_text_setup_needed, true);
            case ON:
                return new WidgetContent(R.string.widget_state_on, R.drawable.ic_widget_mutify_on,
                        R.drawable.widget_background_on, R.color.widget_text_on, false);
            case AD_MUTED:
                return new WidgetContent(R.string.status_ad_muted, R.drawable.ic_widget_mutify_ad_muted,
                        R.drawable.widget_background_on, R.color.widget_text_on, false);
            case OFF:
            default:
                return new WidgetContent(R.string.widget_state_off, R.drawable.ic_widget_mutify_off,
                        R.drawable.widget_background_off, R.color.widget_text_off, false);
        }
    }

    /** The label goes beside the icon (2x1 and wider) rather than below it (1x1). */
    static boolean isWide(int minWidthDp) {
        return minWidthDp >= WIDE_MIN_WIDTH_DP;
    }
}
