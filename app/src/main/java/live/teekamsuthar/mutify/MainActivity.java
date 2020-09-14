package live.teekamsuthar.mutify;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity implements ReceiverCallback {

    private Intent loggerService;
    private SwitchMaterial adSwitch;
    private SpotifyBroadcastReceiver mBroadcastListener;
    private AudioManager audioManager;
    private boolean isMuted;
    private TextView id, artist, album, track, length, playbackPosition, playing, timeSent, registeredTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        adSwitch = findViewById(R.id.adSwitch);

        // initialize all TextViews
        id = findViewById(R.id.trackid);
        artist = findViewById(R.id.artist);
        album = findViewById(R.id.album);
        track = findViewById(R.id.track);
        length = findViewById(R.id.length);
        playbackPosition = findViewById(R.id.playbackPosition);
        playing = findViewById(R.id.isPlaying);
        timeSent = findViewById(R.id.timeSent);
        registeredTime = findViewById(R.id.registeredTime);

        // initialize audio manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // Create the broadcast listener and register the filters
        mBroadcastListener = new SpotifyBroadcastReceiver(this);
    }

    @Override
    public void metadataChanged(Song song) {
        updateSongTextView(song);
    }

    private void updateSongTextView(Song song) {
        // live update all TextViews with received broadcast
        id.setText(String.format("ID: %s", song.getId()));
        artist.setText(String.format("ARTIST: %s", song.getArtist()));
        album.setText(String.format("ALBUM: %s", song.getAlbum()));
        track.setText(String.format("TRACK: %s", song.getTrack()));
        length.setText(String.format("LENGTH: %s", song.getLength().toString()));
        timeSent.setText(String.format("TIMESENT: %s", song.getTimeSent().toString()));
        registeredTime.setText(String.format("REGTIME: %s", song.getRegisteredTime().toString()));
    }

    @Override
    public void playbackStateChanged(boolean isPlaying, int playbackPos) {
        playbackPosition.setText("PLAYBACKPOS:" + playbackPos);
        playing.setText(String.format("PLAYING: %s", isPlaying));
    }

    private int getMusicVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    private void mute() {
        isMuted = true;
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
    }

    private void unmute() {
        isMuted = false;
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
    }

    public void checkSwitch(View view) {
        loggerService = new Intent(MainActivity.this, LoggingService.class);
        if (adSwitch.isChecked()) {
            startService();
        } else {
            stopService();
        }
    }

    private void startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(loggerService);
        } else {
            startService(loggerService);
        }
        // intent filters for broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED);
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED);

        registerReceiver(mBroadcastListener, filter);
        Toast.makeText(MainActivity.this, "service started...", Toast.LENGTH_SHORT).show();
    }

    private void stopService() {
        stopService(loggerService);
        unregisterReceiver(mBroadcastListener);
        Toast.makeText(MainActivity.this, "service stopped...", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        stopService(loggerService);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}