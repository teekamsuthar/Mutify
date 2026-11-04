package live.teekamsuthar.mutify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static live.teekamsuthar.mutify.MainActivity.closeSwitch;
import static live.teekamsuthar.mutify.NotificationService.ACTION_STOP;

public class StopServiceBroadcastReceiver extends BroadcastReceiver {

    public static boolean shouldCloseService = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("Action");
        if (ACTION_STOP.equals(action)) {
            // stop muting even when MainActivity is gone; the listener service keeps running
            SpotifyAdListenerService.setMutingEnabled(context, false);
            context.stopService(new Intent(context, NotificationService.class));
            closeSwitch();
            shouldCloseService = true;
        }
        Log.d("Receiver", "RECEIVED BROADCAST");

//        Intent bringAppToForeground = new Intent(context, MainActivity.class);
//        bringAppToForeground.setAction(Intent.ACTION_MAIN);
//        bringAppToForeground.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        bringAppToForeground.addCategory(Intent.CATEGORY_LAUNCHER);
//        context.startActivity(bringAppToForeground);
    }

}
