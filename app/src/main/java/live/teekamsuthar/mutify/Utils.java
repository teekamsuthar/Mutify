package live.teekamsuthar.mutify;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class Utils {

    public static boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            Intent i = manager.getLaunchIntentForPackage(packageName);
            if (i == null) {
                return false;
                //throw new ActivityNotFoundException();
            }
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    public static String getTimeStamp(long millis) {
        // long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = (millis / 1000) % 60;
        return String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds);
    }

    public static String getTimeStampFromDate(long timeStamp) {
        Date date = new Date(timeStamp);
        DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(date);
    }
//            Play store in-app review TODO haven't tested yet.
//            final ReviewManager manager = ReviewManagerFactory.create(this);
//            ReviewManager manager = new FakeReviewManager(this);
//            Task<ReviewInfo> request = manager.requestReviewFlow();
//            request.addOnCompleteListener(task -> {
//                if (task.isSuccessful()) {
//                    // We can get the ReviewInfo object
//                    ReviewInfo reviewInfo = task.getResult();
//                    Task<Void> flow = manager.launchReviewFlow(MainActivity.this, reviewInfo);
//                    flow.addOnCompleteListener(request1 -> {
//                        System.out.println(request1);
//                        // The flow has finished. The API does not indicate whether the user
//                        // reviewed or not, or even whether the review dialog was shown. Thus, no
//                        // matter the result, we continue our app flow.
//                    });
//                    System.out.println(reviewInfo);
//                } else {
//                    // There was some problem, continue regardless of the result.
//                    System.out.println("something went wrong");
//                }
//            });

}
