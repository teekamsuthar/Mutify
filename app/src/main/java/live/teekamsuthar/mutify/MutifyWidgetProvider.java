package live.teekamsuthar.mutify;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SizeF;
import android.widget.RemoteViews;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

/**
 * The home-screen widget: shows Mutify's {@link MutifyStatus} and switches muting on or off when
 * tapped; with setup needed, a tap opens the app instead and never touches the setting.
 * <p>
 * 1x1 by default (label below the icon), resizable to 2x1 (label beside it). {@link StatusPublisher}
 * calls {@link #updateAll(Context, MutifyStatus)} on every status change; {@link #onUpdate} renders
 * {@link StatusPublisher#currentStatus()} when the launcher asks (widget placed, launcher restarted,
 * phone rebooted).
 */
public class MutifyWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "MutifyWidget";
    private static final String ACTION_TOGGLE = "live.teekamsuthar.mutify.action.WIDGET_TOGGLE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        MutifyStatus status = StatusPublisher.get(context).currentStatus();
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, views(context, status, manager.getAppWidgetOptions(id)));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId,
                                          Bundle newOptions) {
        // resized: below Android 12 the layout depends on the new width
        manager.updateAppWidget(appWidgetId,
                views(context, StatusPublisher.get(context).currentStatus(), newOptions));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            toggle(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    private static void toggle(Context context) {
        StatusPublisher publisher = StatusPublisher.get(context);
        MutifyStatus status = publisher.currentStatus();
        if (status == MutifyStatus.SETUP_NEEDED) {
            // the widget still showed a toggle (access revoked since): never write the setting,
            // open the app instead and redraw, so the next tap opens the app directly
            openApp(context);
            updateAll(context, status);
            return;
        }
        MutifySettings.get(context).setEnabled(!status.isOn());
        // apply() has updated the in-memory value already: publish now instead of waiting for the
        // preference listener, which runs only after this receiver has returned
        publisher.refresh();
    }

    private static void openApp(Context context) {
        try {
            context.startActivity(appIntent(context));
        } catch (RuntimeException e) {
            // a background activity start may be refused; the redrawn widget opens the app directly
            Log.w(TAG, "could not open the app from the widget", e);
        }
    }

    /** Redraws every placed widget for {@code status}; called by {@link StatusPublisher}. */
    static void updateAll(@NonNull Context context, @NonNull MutifyStatus status) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return; // no widget support on this device
        }
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, MutifyWidgetProvider.class));
        for (int id : ids) {
            manager.updateAppWidget(id, views(context, status, manager.getAppWidgetOptions(id)));
        }
    }

    private static RemoteViews views(Context context, MutifyStatus status, @Nullable Bundle options) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return responsiveViews(context, status);
        }
        int minWidth = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        return views(context, status, WidgetContent.isWide(minWidth) ? R.layout.widget_wide : R.layout.widget_small);
    }

    /** Android 12+: the launcher picks the layout for the widget's current size by itself. */
    @RequiresApi(Build.VERSION_CODES.S)
    private static RemoteViews responsiveViews(Context context, MutifyStatus status) {
        Map<SizeF, RemoteViews> layouts = new HashMap<>();
        layouts.put(new SizeF(0f, 0f), views(context, status, R.layout.widget_small));
        layouts.put(new SizeF(WidgetContent.WIDE_MIN_WIDTH_DP, 0f), views(context, status, R.layout.widget_wide));
        return new RemoteViews(layouts);
    }

    private static RemoteViews views(Context context, MutifyStatus status, @LayoutRes int layout) {
        WidgetContent content = WidgetContent.of(status);
        String label = context.getString(content.label);
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setImageViewResource(R.id.widget_icon, content.icon);
        views.setTextViewText(R.id.widget_label, label);
        views.setTextColor(R.id.widget_label, ContextCompat.getColor(context, content.labelColor));
        views.setInt(R.id.widget_root, "setBackgroundResource", content.background);
        views.setContentDescription(R.id.widget_root,
                context.getString(R.string.widget_content_description, label));
        views.setOnClickPendingIntent(R.id.widget_root,
                content.tapOpensApp ? openAppIntent(context) : toggleIntent(context));
        return views;
    }

    private static PendingIntent toggleIntent(Context context) {
        Intent intent = new Intent(ACTION_TOGGLE)
                .setComponent(new ComponentName(context, MutifyWidgetProvider.class));
        return PendingIntent.getBroadcast(context, 0, intent, immutableFlags());
    }

    // opens the main screen straight into the onboarding dialog
    private static PendingIntent openAppIntent(Context context) {
        return PendingIntent.getActivity(context, 0, appIntent(context), immutableFlags());
    }

    private static Intent appIntent(Context context) {
        return Onboarding.openAppIntent(context);
    }

    private static int immutableFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
