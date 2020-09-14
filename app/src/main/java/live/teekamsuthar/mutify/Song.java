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
                Integer playbackPosition,
                boolean playing,
                Long timeSent,
                Long registeredTime) {
        this.id = id;
        this.artist = artist;
        this.album = album;
        this.track = track;
        this.length = length;
        this.playbackPosition = playbackPosition;
        this.playing = playing;
        this.timeSent = timeSent;
        this.registeredTime = registeredTime;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
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

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public Long getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(Long timeSent) {
        this.timeSent = timeSent;
    }

    public Long getRegisteredTime() {
        return registeredTime;
    }

    public void setRegisteredTime(Long registeredTime) {
        this.registeredTime = registeredTime;
    }

    public Long timeRemaining() {
        return (long) (length - playbackPosition);
    }

    public Long getTimeFinish() {
        return timeSent + timeRemaining();
    }

    public Long getPropagation() {
        return System.currentTimeMillis() - timeSent;
    }

}


