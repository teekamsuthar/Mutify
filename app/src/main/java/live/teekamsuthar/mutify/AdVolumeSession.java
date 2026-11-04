package live.teekamsuthar.mutify;

/**
 * The volume rules for one ad (or a run of back-to-back ads): what to do with the music stream
 * when the ad starts, while it plays, and when it is over.
 * <p>
 * Pure Java, no Android types, so the rules run in plain JVM tests. {@link SpotifyAdListenerService}
 * feeds it the music stream's volume (0 while the stream is muted) and carries out the
 * {@link Action}s it returns.
 * <p>
 * During the ad the chosen step wins: the ad stays at the chosen level whatever happens to the
 * volume, and afterwards the volume from before the ad comes back.
 * <ul>
 *   <li>{@link #begin(int, int, int)}: step 0 mutes the stream; steps 1–5 lower it to 10–50 % of
 *   the current volume, rounded, but never to silence and never to the current volume itself.
 *   At volume 1 or below a quieter step has nothing to lower, so the volume stays.</li>
 *   <li>{@link #observe(int)}: the volume changed during the ad. If it is not what the step means
 *   (a volume key pressed by accident, the stream unmuted), the step is applied again.</li>
 *   <li>{@link #changeStep(int, int)}: the user picked another step during the ad. It applies to
 *   the running ad at once, computed from the volume before the ad with the same rules as
 *   {@link #begin}.</li>
 *   <li>{@link #end(int)}: unmute, or restore the exact volume from before the ad. With the mute
 *   step, a volume the user set with the volume keys during the ad is kept: muting leaves the
 *   volume underneath alone, so unmuting is enough.</li>
 * </ul>
 * If the user had muted the stream before the ad, Mutify leaves it muted — until the user unmutes
 * it during the ad: from then on the ad is handled as if it began at that moment, with the
 * unmuted volume as the volume to come back to.
 * <p>
 * A {@link #begin} while the session is still active (the next ad started before the previous
 * one's end was carried out) continues the session, so the volume from before the first ad is the
 * one restored.
 */
public final class AdVolumeSession {

    /** Step 0: mute the stream during ads. */
    public static final int STEP_MUTE = 0;
    /** Highest step: ads at 50 % of the current volume. */
    public static final int MAX_STEP = 5;
    /** Each step above {@link #STEP_MUTE} is this many percent of the current volume. */
    private static final int PERCENT_PER_STEP = 10;
    /**
     * Corrections in a row that did not bring the stream to the step's level. Beyond this the
     * system evidently refuses or alters the change (fixed volume, volume limits), and correcting
     * again would only loop on the volume broadcasts.
     */
    static final int MAX_CORRECTIONS = 5;

    public enum Kind {
        /** Leave the stream as it is. */
        NONE,
        /** Mute the music stream. */
        MUTE,
        /** Set the music stream to {@link Action#volume}. */
        SET_VOLUME,
        /** Unmute the music stream. */
        UNMUTE,
        /** Set the music stream back to {@link Action#volume}, the volume from before the ad. */
        RESTORE,
        /**
         * Set the muted music stream to {@link Action#volume}, then unmute it — in that order, so the
         * volume it was muted at never plays.
         */
        UNMUTE_AND_SET_VOLUME,
        /** Like {@link #UNMUTE_AND_SET_VOLUME}, with the volume from before the ad. */
        UNMUTE_AND_RESTORE
    }

    /** What to do with the music stream. {@link #volume} is only meaningful when {@link #setsVolume()}. */
    public static final class Action {

        private static final Action NONE = new Action(Kind.NONE, 0);
        private static final Action MUTE = new Action(Kind.MUTE, 0);
        private static final Action UNMUTE = new Action(Kind.UNMUTE, 0);

        public final Kind kind;
        public final int volume;

        private Action(Kind kind, int volume) {
            this.kind = kind;
            this.volume = volume;
        }

        public static Action none() {
            return NONE;
        }

        public static Action mute() {
            return MUTE;
        }

        public static Action unmute() {
            return UNMUTE;
        }

        public static Action setVolume(int volume) {
            return new Action(Kind.SET_VOLUME, volume);
        }

        public static Action restore(int volume) {
            return new Action(Kind.RESTORE, volume);
        }

        public static Action unmuteAndSetVolume(int volume) {
            return new Action(Kind.UNMUTE_AND_SET_VOLUME, volume);
        }

        public static Action unmuteAndRestore(int volume) {
            return new Action(Kind.UNMUTE_AND_RESTORE, volume);
        }

        /** The action sets the stream to {@link #volume}. */
        public boolean setsVolume() {
            return kind == Kind.SET_VOLUME || kind == Kind.RESTORE
                    || kind == Kind.UNMUTE_AND_SET_VOLUME || kind == Kind.UNMUTE_AND_RESTORE;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Action)) return false;
            Action other = (Action) o;
            return kind == other.kind && volume == other.volume;
        }

        @Override
        public int hashCode() {
            return 31 * kind.hashCode() + volume;
        }

        @Override
        public String toString() {
            return setsVolume() ? kind + "(" + volume + ")" : kind.toString();
        }
    }

    private boolean active;
    /**
     * The volume before the ad, to come back to afterwards; 0 while the user keeps the stream muted
     * that was muted before the ad, and Mutify leaves the ad alone.
     */
    private int savedVolume;
    /** The step in force for this ad. */
    private int step;
    /** With the mute step: the volume underneath the mute, which unmuting brings back. */
    private int mutedAt;
    /** With the mute step: the user moved the volume underneath the mute during the ad. */
    private boolean userMovedMutedVolume;
    /** Corrections in a row since the stream was last seen at the step's level. */
    private int corrections;

    /** An ad is playing, or just ended and {@link #end} has not been called yet. */
    public boolean isActive() {
        return active;
    }

    /**
     * An ad started.
     *
     * @param currentVolume the music stream's volume, 0 while it is muted
     * @param maxVolume     the music stream's maximum volume
     * @param step          {@link #STEP_MUTE}, or 1–{@link #MAX_STEP} for 10–50 % of the current volume
     * @return MUTE, SET_VOLUME(target), or NONE when the stream is already muted, the volume is too
     * low to lower, or the session is already active
     */
    public Action begin(int currentVolume, int maxVolume, int step) {
        if (active) {
            return Action.none(); // back-to-back ads: keep what the first ad saved
        }
        active = true;
        this.step = clampStep(step);
        savedVolume = 0;
        int current = Math.max(0, Math.min(currentVolume, maxVolume));
        if (current == 0) {
            return Action.none(); // the user muted before the ad: leave it muted until they unmute
        }
        return startAt(current);
    }

    /**
     * The user picked another step while the session is active. It applies to the running ad at
     * once, computed from the volume before the ad with the same rules as {@link #begin}.
     *
     * @param currentVolume the music stream's volume now, 0 while it is muted
     * @param step          {@link #STEP_MUTE}, or 1–{@link #MAX_STEP} for 10–50 % of the volume
     *                      before the ad
     * @return MUTE, SET_VOLUME(target), UNMUTE_AND_SET_VOLUME(target), or NONE when no session is
     * active, the stream muted before the ad is still muted, or the stream is already where the
     * step wants it
     */
    public Action changeStep(int currentVolume, int step) {
        if (!active) {
            return Action.none();
        }
        int newStep = clampStep(step);
        if (savedVolume == 0) {
            this.step = newStep; // for when the user unmutes
            // unmuted, and the observer missed it: the ad starts now, with the new step
            return currentVolume > 0 ? startAt(currentVolume) : Action.none();
        }
        corrections = 0;
        if (newStep == STEP_MUTE && this.step != STEP_MUTE) {
            // muting keeps the volume underneath: the step's volume, unless the stream shows another
            mutedAt = currentVolume > 0 ? currentVolume : targetVolume();
            userMovedMutedVolume = false;
        }
        this.step = newStep;
        return towardsStep(currentVolume);
    }

    /**
     * The music stream's volume changed during the ad (0 while muted).
     *
     * @return what puts the stream back to the step's level: MUTE, SET_VOLUME(target),
     * UNMUTE_AND_SET_VOLUME(target), or NONE when it is there already (also Mutify's own change),
     * no session is active, the stream muted before the ad is still muted, or {@link #MAX_CORRECTIONS}
     * corrections in a row had no effect
     */
    public Action observe(int volume) {
        if (!active) {
            return Action.none();
        }
        if (savedVolume == 0) {
            // muted before the ad: unmuting during it starts the ad's handling now
            return volume > 0 ? startAt(volume) : Action.none();
        }
        Action correction = towardsStep(volume);
        if (correction.kind == Kind.NONE) {
            corrections = 0;
            return correction;
        }
        if (corrections >= MAX_CORRECTIONS) {
            return Action.none(); // the system does not take the change; stop instead of looping
        }
        corrections++;
        if (step == STEP_MUTE) {
            mutedAt = volume; // a volume key unmuted the stream at this volume
            userMovedMutedVolume = true;
        }
        return correction;
    }

    /**
     * The ad is over, or Mutify was switched off during it. Ends the session.
     *
     * @param currentVolume the music stream's volume now, 0 while it is muted
     * @return UNMUTE, RESTORE(volume before the ad), UNMUTE_AND_RESTORE(volume before the ad), or
     * NONE when the stream is already at the volume before the ad, or the stream muted before the
     * ad stayed muted
     */
    public Action end(int currentVolume) {
        if (!active) {
            return Action.none();
        }
        active = false;
        if (savedVolume == 0) {
            return Action.none();
        }
        if (step == STEP_MUTE) {
            if (currentVolume > 0) {
                return Action.none(); // unmuted already; the user chose this volume with the volume keys
            }
            return userMovedMutedVolume || mutedAt == savedVolume
                    ? Action.unmute() : Action.unmuteAndRestore(savedVolume);
        }
        if (currentVolume == 0) {
            return Action.unmuteAndRestore(savedVolume);
        }
        return currentVolume == savedVolume ? Action.none() : Action.restore(savedVolume);
    }

    /** Saves {@code volume} as the one to come back to and applies the step in force. */
    private Action startAt(int volume) {
        savedVolume = volume;
        mutedAt = volume;
        userMovedMutedVolume = false;
        corrections = 0;
        return towardsStep(volume);
    }

    private static int clampStep(int step) {
        return Math.max(STEP_MUTE, Math.min(step, MAX_STEP));
    }

    /** What takes the stream from {@code currentVolume} to the step's level; NONE when it is there. */
    private Action towardsStep(int currentVolume) {
        if (step == STEP_MUTE) {
            return currentVolume == 0 ? Action.none() : Action.mute();
        }
        int target = targetVolume();
        if (currentVolume == target) {
            return Action.none();
        }
        return currentVolume == 0 ? Action.unmuteAndSetVolume(target) : Action.setVolume(target);
    }

    /**
     * round(volume before the ad × the step's percent), at least 1 and at most that volume − 1; at
     * volume 1 nothing is quieter except silence, which only the mute step means.
     */
    private int targetVolume() {
        if (savedVolume <= 1) {
            return savedVolume;
        }
        int percent = step * PERCENT_PER_STEP;
        int target = (savedVolume * percent + 50) / 100; // rounded half up, in whole numbers
        return Math.max(1, Math.min(target, savedVolume - 1));
    }
}
