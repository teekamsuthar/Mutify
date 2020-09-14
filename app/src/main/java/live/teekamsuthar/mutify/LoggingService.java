package live.teekamsuthar.mutify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class LoggingService extends Service {

    public static final String NOTIFICATION_CHANNEL_ID_SERVICE = "live.teekamsuthar.mutify.service";
    public static final String NOTIFICATION_CHANNEL_ID_INFO = "live.teekamsuthar.mutify.notification_info";

    @Override
    public void onCreate() {
        super.onCreate();
        // create notification channel for devices above api 26/Oreo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, NOTIFICATION_CHANNEL_ID_INFO, NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE)
                    .setContentText("This is running in Background")
                    .setContentTitle("Background service")
                    .setSmallIcon(R.drawable.ic_launcher_foreground);
            startForeground(101, builder.build());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
