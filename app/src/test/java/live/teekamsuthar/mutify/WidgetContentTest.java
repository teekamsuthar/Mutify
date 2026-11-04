package live.teekamsuthar.mutify;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WidgetContentTest {

    @Test
    public void everyStatusHasItsOwnLabel() {
        Set<Integer> labels = new HashSet<>();
        for (MutifyStatus status : MutifyStatus.values()) {
            labels.add(WidgetContent.of(status).label);
        }
        assertEquals(MutifyStatus.values().length, labels.size());
    }

    @Test
    public void setupNeededOpensTheAppOnTap() {
        assertTrue(WidgetContent.of(MutifyStatus.SETUP_NEEDED).tapOpensApp);
    }

    @Test
    public void offOnAndAdMutedToggleOnTap() {
        assertFalse(WidgetContent.of(MutifyStatus.OFF).tapOpensApp);
        assertFalse(WidgetContent.of(MutifyStatus.ON).tapOpensApp);
        assertFalse(WidgetContent.of(MutifyStatus.AD_MUTED).tapOpensApp);
    }

    @Test
    public void setupNeededLooksSwitchedOff() {
        assertEquals(WidgetContent.of(MutifyStatus.OFF).background,
                WidgetContent.of(MutifyStatus.SETUP_NEEDED).background);
        assertEquals(WidgetContent.of(MutifyStatus.OFF).icon, WidgetContent.of(MutifyStatus.SETUP_NEEDED).icon);
    }

    @Test
    public void onAndAdMutedLookSwitchedOn() {
        assertEquals(WidgetContent.of(MutifyStatus.ON).background, WidgetContent.of(MutifyStatus.AD_MUTED).background);
        assertNotEquals(WidgetContent.of(MutifyStatus.OFF).background, WidgetContent.of(MutifyStatus.ON).background);
    }

    @Test
    public void adMutedHighlightsTheMuteIcon() {
        assertNotEquals(WidgetContent.of(MutifyStatus.ON).icon, WidgetContent.of(MutifyStatus.AD_MUTED).icon);
    }

    @Test
    public void oneCellWidthKeepsTheLabelBelowTheIcon() {
        assertFalse(WidgetContent.isWide(40));
        assertFalse(WidgetContent.isWide(WidgetContent.WIDE_MIN_WIDTH_DP - 1));
    }

    @Test
    public void twoCellWidthPutsTheLabelBesideTheIcon() {
        assertTrue(WidgetContent.isWide(WidgetContent.WIDE_MIN_WIDTH_DP));
        assertTrue(WidgetContent.isWide(110));
    }
}
