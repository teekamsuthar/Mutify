package live.teekamsuthar.mutify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class NotificationService extends Service {

    public static final String NOTIFICATION_CHANNEL_ID_SERVICE = "live.teekamsuthar.mutify.service";
    public static final String NOTIFICATION_CHANNEL_ID_INFO = "Ad muting service";
    public static final String ACTION_STOP = "STOP_SERVICE";

    @Override
    public void onCreate() {
        super.onCreate();
        // action to be added in the notification
        Intent intent = new Intent(this, StopServiceBroadcastReceiver.class);
        intent.putExtra("Action", ACTION_STOP);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action action = new NotificationCompat.Action.Builder(R.drawable.ic_baseline_close_24, "Stop", pendingIntent).build();

        // create notification channel for devices above api 26/Oreo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, NOTIFICATION_CHANNEL_ID_INFO, NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE)
                    .setContentTitle("Mutify Service")
                    .setContentText("Mutify is running in the background.")
                    .setContentIntent(notificationPendingIntent())
                    .addAction(action)
                    .setSmallIcon(R.drawable.mutify_logo_without_bg);
            startForeground(101, builder.build());
        }
        Toast.makeText(this, "Enjoy your ad-free music ;)", Toast.LENGTH_SHORT).show();
    }

//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (intent != null && intent.getAction() != null) {
//            if (intent.getAction().equals(ACTION_STOP)) {
//                System.out.println("Stopped");
//            }
//        }
//        return START_STICKY;
//    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service stopped...", Toast.LENGTH_SHORT).show();
    }

    private PendingIntent notificationPendingIntent() {
        final Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
