package live.teekamsuthar.mutify;

import org.junit.Test;

import live.teekamsuthar.mutify.AdVolumeSession.Action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdVolumeSessionTest {

    private static final int MAX = 15;

    private final AdVolumeSession session = new AdVolumeSession();

    @Test
    public void muteStepMutesAndUnmutesAfterTheAd() {
        assertEquals(Action.mute(), session.begin(12, MAX, AdVolumeSession.STEP_MUTE));
        assertTrue(session.isActive());
        assertEquals(Action.none(), session.observe(0)); // Mutify's own mute
        assertEquals(Action.unmute(), session.end(0));
        assertFalse(session.isActive());
    }

    @Test
    public void eachPercentStepLowersToThatShareOfTheCurrentVolume() {
        // 12 × 10 % = 1.2, × 20 % = 2.4, × 30 % = 3.6, × 40 % = 4.8, × 50 % = 6
        int[] expected = {1, 2, 4, 5, 6};
        for (int step = 1; step <= AdVolumeSession.MAX_STEP; step++) {
            AdVolumeSession s = new AdVolumeSession();
            assertEquals("step " + step, Action.setVolume(expected[step - 1]), s.begin(12, MAX, step));
        }
    }

    @Test
    public void halfwayRoundsUp() {
        assertEquals(Action.setVolume(5), session.begin(15, MAX, 3)); // 4.5
    }

    @Test
    public void atVolumeOneAQuieterStepLeavesTheVolume() {
        for (int step = 1; step <= AdVolumeSession.MAX_STEP; step++) {
            AdVolumeSession s = new AdVolumeSession();
            assertEquals("step " + step, Action.none(), s.begin(1, MAX, step));
            assertEquals("step " + step, Action.none(), s.end(1));
        }
    }

    @Test
    public void atVolumeOneTheMuteStepStillMutes() {
        assertEquals(Action.mute(), session.begin(1, MAX, AdVolumeSession.STEP_MUTE));
        assertEquals(Action.unmute(), session.end(0));
    }

    @Test
    public void atVolumeTwoEveryQuieterStepGoesToOneNeverToSilence() {
        for (int step = 1; step <= AdVolumeSession.MAX_STEP; step++) {
            AdVolumeSession s = new AdVolumeSession();
            assertEquals("step " + step, Action.setVolume(1), s.begin(2, MAX, step));
            assertEquals("step " + step, Action.restore(2), s.end(1));
        }
    }

    @Test
    public void atVolumeThreeTheStepsStayBelowTheCurrentVolume() {
        // 0.3, 0.6, 0.9 and 1.2 → at least 1; 1.5 → 2, still below 3
        int[] expected = {1, 1, 1, 1, 2};
        for (int step = 1; step <= AdVolumeSession.MAX_STEP; step++) {
            AdVolumeSession s = new AdVolumeSession();
            assertEquals("step " + step, Action.setVolume(expected[step - 1]), s.begin(3, MAX, step));
        }
    }

    @Test
    public void exactPreviousVolumeIsRestored() {
        assertEquals(Action.setVolume(2), session.begin(12, MAX, 2));
        assertEquals(Action.none(), session.observe(2)); // Mutify's own change
        assertEquals(Action.restore(12), session.end(2));
        assertFalse(session.isActive());
    }

    // ---- the step wins over volume changes during the ad ----

    @Test
    public void volumeKeyDuringAQuieterAdIsCorrected() {
        session.begin(12, MAX, 2);
        assertEquals(Action.setVolume(2), session.observe(7));
    }

    @Test
    public void correctedVolumeIsNotCorrectedAgain() {
        session.begin(12, MAX, 2);
        session.observe(7);
        assertEquals(Action.none(), session.observe(2)); // the correction's own broadcast
    }

    @Test
    public void afterACorrectedQuieterAdTheVolumeFromBeforeTheAdIsRestored() {
        session.begin(12, MAX, 2);
        session.observe(7);
        session.observe(2);
        assertEquals(Action.restore(12), session.end(2));
    }

    @Test
    public void mutingDuringAQuieterAdIsUndoneAtTheStepVolume() {
        session.begin(12, MAX, 2);
        assertEquals(Action.unmuteAndSetVolume(2), session.observe(0));
    }

    @Test
    public void changeTheObserverMissedIsStillUndoneAfterTheAd() {
        session.begin(12, MAX, 2);
        assertEquals(Action.restore(12), session.end(9));
    }

    @Test
    public void streamMutedAtTheEndOfAQuieterAdIsUnmutedAndRestored() {
        session.begin(12, MAX, 2);
        assertEquals(Action.unmuteAndRestore(12), session.end(0));
    }

    @Test
    public void volumeKeyDuringAMutedAdMutesAgain() {
        session.begin(12, MAX, AdVolumeSession.STEP_MUTE);
        assertEquals(Action.mute(), session.observe(8));
        assertEquals(Action.none(), session.observe(0)); // the correction's own broadcast
    }

    @Test
    public void afterAMutedAdTheVolumeTheUserSetWithTheKeysIsKept() {
        session.begin(12, MAX, AdVolumeSession.STEP_MUTE);
        session.observe(8); // unmuted at 8 by a volume key, muted again
        // muting left 8 underneath: unmuting alone keeps it
        assertEquals(Action.unmute(), session.end(0));
    }

    @Test
    public void mutedAdUnmutedByTheUserAtItsEndIsLeftAlone() {
        session.begin(12, MAX, AdVolumeSession.STEP_MUTE);
        assertEquals(Action.none(), session.end(8));
    }

    @Test
    public void correctionsStopWhenTheSystemDoesNotTakeThem() {
        session.begin(12, MAX, 2);
        for (int i = 0; i < AdVolumeSession.MAX_CORRECTIONS; i++) {
            assertEquals("correction " + i, Action.setVolume(2), session.observe(15));
        }
        assertEquals(Action.none(), session.observe(15));
        assertEquals(Action.restore(12), session.end(15));
    }

    @Test
    public void correctionsThatTakeEffectNeverRunOut() {
        session.begin(12, MAX, 2);
        for (int i = 0; i < AdVolumeSession.MAX_CORRECTIONS * 2; i++) {
            assertEquals("press " + i, Action.setVolume(2), session.observe(3));
            assertEquals("press " + i, Action.none(), session.observe(2));
        }
    }

    // ---- other sessions ----

    @Test
    public void userMutedBeforeTheAdStaysMuted() {
        assertEquals(Action.none(), session.begin(0, MAX, AdVolumeSession.STEP_MUTE));
        assertTrue(session.isActive());
        assertEquals(Action.none(), session.end(0));

        AdVolumeSession quieter = new AdVolumeSession();
        assertEquals(Action.none(), quieter.begin(0, MAX, 3));
        assertEquals(Action.none(), quieter.end(0));
    }

    @Test
    public void unmutingDuringTheAdAfterMutingBeforeItAppliesTheStepFromThen() {
        session.begin(0, MAX, 2);
        assertEquals(Action.setVolume(2), session.observe(12)); // as if the ad began at 12
    }

    @Test
    public void unmutingDuringTheAdAfterMutingBeforeItRestoresTheUnmutedVolumeAfterwards() {
        session.begin(0, MAX, 2);
        session.observe(12);
        assertEquals(Action.none(), session.observe(2)); // Mutify's own change
        assertEquals(Action.restore(12), session.end(2));
    }

    @Test
    public void unmutingDuringTheAdAfterMutingBeforeItIsEnforcedFromThen() {
        session.begin(0, MAX, 2);
        session.observe(12);
        assertEquals(Action.setVolume(2), session.observe(9));
    }

    @Test
    public void unmutingDuringAMutedAdAfterMutingBeforeItMutesAgainAndUnmutesAfterwards() {
        session.begin(0, MAX, AdVolumeSession.STEP_MUTE);
        assertEquals(Action.mute(), session.observe(12));
        assertEquals(Action.none(), session.observe(0));
        assertEquals(Action.unmute(), session.end(0));
    }

    @Test
    public void stepChangedWhileMutedBeforeTheAdAppliesWhenTheUserUnmutes() {
        session.begin(0, MAX, AdVolumeSession.STEP_MUTE);
        assertEquals(Action.none(), session.changeStep(0, 5));
        assertEquals(Action.setVolume(6), session.observe(12));
    }

    @Test
    public void stepChangeNoticesAnUnmuteTheObserverMissedAfterMutingBeforeTheAd() {
        session.begin(0, MAX, AdVolumeSession.STEP_MUTE);
        assertEquals(Action.setVolume(6), session.changeStep(12, 5));
        assertEquals(Action.restore(12), session.end(6));
    }

    @Test
    public void switchingOffMidAdRestoresAtOnceAndEndsTheSession() {
        session.begin(12, MAX, 2);
        // switched off while the ad still plays: end right away, no further ad handling
        assertEquals(Action.restore(12), session.end(2));
        assertFalse(session.isActive());
        assertEquals(Action.none(), session.end(2));
        assertEquals(Action.none(), session.observe(9)); // ignored once the session is over
        assertFalse(session.isActive());
    }

    @Test
    public void backToBackAdsRestoreTheVolumeFromBeforeTheFirstAd() {
        assertEquals(Action.setVolume(2), session.begin(12, MAX, 2));
        // the first ad ended, the delayed end was cancelled because the next ad started
        assertEquals(Action.none(), session.begin(2, MAX, 2));
        assertEquals(Action.restore(12), session.end(2));
    }

    @Test
    public void aNewSessionStartsFresh() {
        session.begin(12, MAX, AdVolumeSession.STEP_MUTE);
        session.observe(8);
        assertEquals(Action.unmute(), session.end(0));
        // the next ad, at the volume the user chose
        assertEquals(Action.setVolume(2), session.begin(8, MAX, 2)); // 1.6
        assertEquals(Action.restore(8), session.end(2));
    }

    @Test
    public void currentVolumeAboveMaxIsCapped() {
        assertEquals(Action.setVolume(8), session.begin(20, MAX, 5)); // 15 × 50 % = 7.5
        assertEquals(Action.restore(MAX), session.end(8));
    }

    // ---- step changed during the ad ----

    @Test
    public void stepChangeWithoutAnActiveSessionDoesNothing() {
        assertEquals(Action.none(), session.changeStep(12, 3));
        assertFalse(session.isActive());
    }

    @Test
    public void percentToOtherPercentUsesTheVolumeFromBeforeTheAd() {
        session.begin(12, MAX, 2); // 2
        assertEquals(Action.setVolume(6), session.changeStep(2, 5)); // 12 × 50 %, not 2 × 50 %
        assertEquals(Action.restore(12), session.end(6));
    }

    @Test
    public void volumeSetByAStepChangeIsNotCorrected() {
        session.begin(12, MAX, 2);
        session.changeStep(2, 5);
        assertEquals(Action.none(), session.observe(6)); // Mutify's own change
    }

    @Test
    public void percentToMuteMutesAndUnmutesToTheVolumeFromBeforeTheAd() {
        session.begin(12, MAX, 2);
        assertEquals(Action.mute(), session.changeStep(2, AdVolumeSession.STEP_MUTE));
        assertEquals(Action.none(), session.observe(0)); // Mutify's own mute
        // the stream is muted at 2 underneath, so unmuting alone would leave it at 2
        assertEquals(Action.unmuteAndRestore(12), session.end(0));
    }

    @Test
    public void muteToPercentSetsTheLowerVolumeAndUnmutes() {
        session.begin(12, MAX, AdVolumeSession.STEP_MUTE);
        assertEquals(Action.unmuteAndSetVolume(2), session.changeStep(0, 2));
        assertEquals(Action.none(), session.observe(2)); // Mutify's own change
        assertEquals(Action.restore(12), session.end(2));
    }

    @Test
    public void muteToPercentAndBackToMuteUnmutesToTheVolumeFromBeforeTheAd() {
        session.begin(12, MAX, AdVolumeSession.STEP_MUTE);
        session.changeStep(0, 2);
        assertEquals(Action.mute(), session.changeStep(2, AdVolumeSession.STEP_MUTE));
        assertEquals(Action.unmuteAndRestore(12), session.end(0));
    }

    @Test
    public void percentToMuteWhileTheStreamIsMutedAlreadyOnlyRemembersTheVolumeUnderneath() {
        session.begin(12, MAX, 2);
        assertEquals(Action.none(), session.changeStep(0, AdVolumeSession.STEP_MUTE));
        assertEquals(Action.unmuteAndRestore(12), session.end(0));
    }

    @Test
    public void stepChangeToTheSameStepDoesNothing() {
        session.begin(12, MAX, 2);
        assertEquals(Action.none(), session.changeStep(2, 2));
        assertEquals(Action.restore(12), session.end(2));
    }

    @Test
    public void stepChangeToAStepWithTheSameTargetDoesNothing() {
        session.begin(3, MAX, 1); // 0.3 → 1
        assertEquals(Action.none(), session.changeStep(1, 3)); // 0.9 → 1
        assertEquals(Action.restore(3), session.end(1));
    }

    @Test
    public void stepChangeAfterAVolumeKeyAppliesTheNewStep() {
        session.begin(12, MAX, 2);
        assertEquals(Action.setVolume(6), session.changeStep(9, 5)); // 9 came from a key the observer missed
        assertEquals(Action.restore(12), session.end(6));
    }

    @Test
    public void stepChangeAfterAVolumeKeyDuringAMutedAdRestoresTheVolumeFromBeforeTheAd() {
        session.begin(12, MAX, AdVolumeSession.STEP_MUTE);
        session.observe(8);
        assertEquals(Action.unmuteAndSetVolume(2), session.changeStep(0, 2));
        assertEquals(Action.restore(12), session.end(2));
    }

    @Test
    public void stepChangeWhenTheUserMutedBeforeTheAdDoesNothing() {
        session.begin(0, MAX, 2);
        assertEquals(Action.none(), session.changeStep(0, AdVolumeSession.STEP_MUTE));
        assertEquals(Action.none(), session.changeStep(0, 4));
        assertEquals(Action.none(), session.end(0));
    }

    @Test
    public void atVolumeOneStepChangeToMuteMutesAndBackUnmutesAtOne() {
        assertEquals(Action.none(), session.begin(1, MAX, 2));
        assertEquals(Action.mute(), session.changeStep(1, AdVolumeSession.STEP_MUTE));
        // nothing quieter than 1: back to 1
        assertEquals(Action.unmuteAndSetVolume(1), session.changeStep(0, 3));
        assertEquals(Action.none(), session.end(1));
    }

    @Test
    public void afterTheSessionEndsAStepChangeDoesNothing() {
        session.begin(12, MAX, 2);
        session.end(2);
        assertEquals(Action.none(), session.changeStep(12, 5));
    }
}
