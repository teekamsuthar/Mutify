package live.teekamsuthar.mutify;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MutifyStatusTest {

    @Test
    public void withoutAccessSetupIsNeededWhateverTheSettingAndAd() {
        assertEquals(MutifyStatus.SETUP_NEEDED, MutifyStatus.of(false, false, false));
        assertEquals(MutifyStatus.SETUP_NEEDED, MutifyStatus.of(false, false, true));
        assertEquals(MutifyStatus.SETUP_NEEDED, MutifyStatus.of(true, false, false));
        assertEquals(MutifyStatus.SETUP_NEEDED, MutifyStatus.of(true, false, true));
    }

    @Test
    public void switchedOffIsOff() {
        assertEquals(MutifyStatus.OFF, MutifyStatus.of(false, true, false));
    }

    @Test
    public void switchedOffIsOffEvenWhileAnAdIsStillReported() {
        // switching off during an ad: the setting wins before the listener reports the ad's end
        assertEquals(MutifyStatus.OFF, MutifyStatus.of(false, true, true));
    }

    @Test
    public void switchedOnWithoutAdIsOn() {
        assertEquals(MutifyStatus.ON, MutifyStatus.of(true, true, false));
    }

    @Test
    public void switchedOnDuringAnAdIsAdMuted() {
        assertEquals(MutifyStatus.AD_MUTED, MutifyStatus.of(true, true, true));
    }

    @Test
    public void onlyOnAndAdMutedCountAsOn() {
        assertTrue(MutifyStatus.ON.isOn());
        assertTrue(MutifyStatus.AD_MUTED.isOn());
        assertFalse(MutifyStatus.OFF.isOn());
        assertFalse(MutifyStatus.SETUP_NEEDED.isOn());
    }
}
