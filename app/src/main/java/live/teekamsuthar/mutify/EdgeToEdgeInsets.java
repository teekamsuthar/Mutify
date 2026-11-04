package live.teekamsuthar.mutify;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Draws a screen edge to edge (forced from targetSdk 35 on) and keeps its content clear of the
 * status bar, the navigation bar and display cutouts.
 */
final class EdgeToEdgeInsets {

    private EdgeToEdgeInsets() {
    }

    /**
     * Call before setContentView. The status bar keeps light icons on the green app bar; the
     * navigation bar follows the light/dark mode.
     */
    static void enable(ComponentActivity activity) {
        EdgeToEdge.enable(activity,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
    }

    /**
     * Pads the app bar below the status bar, so its background fills the area behind it, and pads
     * the scrolling content above the navigation bar, so it scrolls behind it but its end stays
     * reachable. Side insets (landscape, cutouts) pad both.
     */
    static void apply(View appBar, ViewGroup scrollingContent) {
        final int appBarLeft = appBar.getPaddingLeft();
        final int appBarTop = appBar.getPaddingTop();
        final int appBarRight = appBar.getPaddingRight();
        final int appBarBottom = appBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (view, windowInsets) -> {
            Insets bars = systemBars(windowInsets);
            view.setPadding(appBarLeft + bars.left, appBarTop + bars.top,
                    appBarRight + bars.right, appBarBottom);
            return windowInsets;
        });

        final int contentLeft = scrollingContent.getPaddingLeft();
        final int contentTop = scrollingContent.getPaddingTop();
        final int contentRight = scrollingContent.getPaddingRight();
        final int contentBottom = scrollingContent.getPaddingBottom();
        scrollingContent.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(scrollingContent, (view, windowInsets) -> {
            Insets bars = systemBars(windowInsets);
            view.setPadding(contentLeft + bars.left, contentTop,
                    contentRight + bars.right, contentBottom + bars.bottom);
            return windowInsets;
        });
    }

    private static Insets systemBars(WindowInsetsCompat windowInsets) {
        return windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
    }
}
