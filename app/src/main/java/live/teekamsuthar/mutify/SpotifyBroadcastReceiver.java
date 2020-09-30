package live.teekamsuthar.mutify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SpotifyBroadcastReceiver extends BroadcastReceiver {

    static final class BroadcastTypes {
        // types of broadcasts that spotify can send!
        static final String SPOTIFY_PACKAGE = "com.spotify.music";
        static final String PLAYBACK_STATE_CHANGED = SPOTIFY_PACKAGE + ".playbackstatechanged";
        static final String QUEUE_CHANGED = SPOTIFY_PACKAGE + ".queuechanged";
        static final String METADATA_CHANGED = SPOTIFY_PACKAGE + ".metadatachanged";
    }

    private ReceiverCallback receiverCallback;

    public SpotifyBroadcastReceiver(ReceiverCallback callback) {
        // initialize callback
        this.receiverCallback = callback;
    }

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
                Song songMetadata = new Song(
                        intent.getStringExtra("id"),
                        intent.getStringExtra("artist"),
                        intent.getStringExtra("album"),
                        intent.getStringExtra("track"),
                        intent.getIntExtra("length", 0),
                        intent.getLongExtra("timeSent", -1L),
                        System.currentTimeMillis()
                );
                // Do something with extracted information...
                receiverCallback.metadataChanged(songMetadata); // callback function
                // additional things to do
                // System.out.println("metadata changed!"); // to log activity

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
                // Do something with extracted information
                receiverCallback.playbackStateChanged(playing, positionInMs, song);
                // additional things to do
                if (playing) {
                    System.out.println("psc: PLAYING!"); // to log activity for troubleshooting
                } else {
                    System.out.println("psc: PAUSED!"); // to log activity
                }
                break;
            case BroadcastTypes.QUEUE_CHANGED:
                // Sent only as a notification, Mutify app may want to respond accordingly.
                // Toast.makeText(context, "queue changed", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}