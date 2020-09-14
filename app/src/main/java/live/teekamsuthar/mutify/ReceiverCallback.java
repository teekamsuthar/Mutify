package live.teekamsuthar.mutify;

public interface ReceiverCallback {
    // Declaration of the callback template function for the interface
    public void metadataChanged(Song song); // when metadata is changed!

    public void playbackStateChanged(boolean isPlaying, int playbackPos); // when playback state is changed!
}
