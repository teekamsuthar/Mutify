package live.teekamsuthar.mutify;

public class Song {
    private String id;
    private String artist;
    private String album;
    private String track;
    private Integer length;
    private Integer playbackPosition;
    private boolean playing;
    private Long timeSent;
    private Long registeredTime;

    public Song(String id,
                String artist,
                String album,
                String track,
                Integer length,
                Long timeSent,
                Long registeredTime) {
        this.id = id;
        this.artist = artist;
        this.album = album;
        this.track = track;
        this.length = length;
        this.timeSent = timeSent;
        this.registeredTime = registeredTime;
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


