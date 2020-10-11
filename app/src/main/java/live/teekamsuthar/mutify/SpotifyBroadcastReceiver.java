package live.teekamsuthar.mutify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SpotifyBroadcastReceiver extends BroadcastReceiver {

    static final class BroadcastTypes {
        // spotify package
        static final String SPOTIFY_PACKAGE = "com.spotify.music";
        // types of broadcasts that spotify can send!
        static final String PLAYBACK_STATE_CHANGED = SPOTIFY_PACKAGE + ".playbackstatechanged";
        static final String QUEUE_CHANGED = SPOTIFY_PACKAGE + ".queuechanged";
        static final String METADATA_CHANGED = SPOTIFY_PACKAGE + ".metadatachanged";
    }

    private ReceiverCallback receiverCallback;

    public SpotifyBroadcastReceiver(ReceiverCallback callback) {
        // initialize callback
        this.receiverCallback = callback;
    }

    // empty constructor to silence manifest warning!
    public SpotifyBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // This is sent with all broadcasts, regardless of type. The value (timeSentInMs) is taken from System.currentTimeMillis(),
        // which you can compare to in order to determine how old the event is.
        // long timeSentInMs = intent.getLongExtra("timeSent", 0L);

        String action = intent.getAction();
        assert action != null;
        switch (action) {
            case BroadcastTypes.METADATA_CHANGED:
                // create a new song from received intent.
                Song songForMetadata = new Song(
                        intent.getStringExtra("id"),
                        intent.getStringExtra("artist"),
                        intent.getStringExtra("album"),
                        intent.getStringExtra("track"),
                        intent.getIntExtra("length", 0),
                        intent.getLongExtra("timeSent", -1L),
                        System.currentTimeMillis()
                );
                // send it off to MainActivity with callback
                receiverCallback.metadataChanged(songForMetadata); // callback function
                break;

            case BroadcastTypes.PLAYBACK_STATE_CHANGED:
                // get info variables
                boolean playing = intent.getBooleanExtra("playing", false);
                int positionInMs = intent.getIntExtra("playbackPosition", 0);
                Song song = new Song(
                        intent.getStringExtra("id"),
                        intent.getStringExtra("artist"),
                        intent.getStringExtra("album"),
                        intent.getStringExtra("track"),
                        intent.getIntExtra("length", 0),
                        intent.getLongExtra("timeSent", -1L),
                        System.currentTimeMillis()
                );
                // send it off to MainActivity with callback
                receiverCallback.playbackStateChanged(playing, positionInMs, song);
                // additional things to do
                if (playing) { // to log activity
                    Log.i("PSC", "PLAYING!");
                } else {
                    Log.i("PSC", "PAUSED!");
                }
                break;

            case BroadcastTypes.QUEUE_CHANGED:
                // Sent only as a notification, Mutify app may want to respond accordingly.
                // Toast.makeText(context, "queue changed", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}