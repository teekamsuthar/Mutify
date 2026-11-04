package live.teekamsuthar.mutify;

/** A Spotify item as the UI shows it; its texts are never null ("" when Spotify sent none). */
public class Song {
    private final String id;
    private final String artist;
    private final String album;
    private final String track;
    private final Integer length;
    private Integer playbackPosition;
    private boolean playing;
    private final Long timeSent;
    private final Long registeredTime;

    public Song(String id,
                String artist,
                String album,
                String track,
                Integer length,
                Long timeSent,
                Long registeredTime) {
        // Spotify's metadata can lack any of these: store "" and 0, so showing or logging them is safe
        this.id = orEmpty(id);
        this.artist = orEmpty(artist);
        this.album = orEmpty(album);
        this.track = orEmpty(track);
        this.length = length == null ? 0 : length;
        this.timeSent = timeSent == null ? 0L : timeSent;
        this.registeredTime = registeredTime == null ? 0L : registeredTime;
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    public String getId() {
        return id;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getTrack() {
        return track;
    }

    public Integer getLength() {
        return length;
    }

    public Integer getPlaybackPosition() {
        return playbackPosition;
    }

    public void setPlaybackPosition(Integer playbackPosition) {
        this.playbackPosition = playbackPosition;
    }

    public boolean getPlaying() {
        return playing;
    }

    public Long getTimeSent() {
        return timeSent;
    }

    public Long getRegisteredTime() {
        return registeredTime;
    }

    public Long timeRemaining(int playPos) {
        return (long) (length - playPos);
    }

    public Long getTimeFinish(int playPos) {
        return timeSent + timeRemaining(playPos);
    }

    public Long getElapsedTime() {
        return System.currentTimeMillis() - timeSent;
    }

}


