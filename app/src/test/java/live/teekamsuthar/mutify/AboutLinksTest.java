package live.teekamsuthar.mutify;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AboutLinksTest {

    @Test
    public void emptyUrlIsHidden() {
        assertFalse(AboutLinks.isShown(""));
    }

    @Test
    public void missingUrlIsHidden() {
        assertFalse(AboutLinks.isShown(null));
    }

    @Test
    public void blankUrlIsHidden() {
        assertFalse(AboutLinks.isShown("   "));
    }

    @Test
    public void filledUrlIsShown() {
        assertTrue(AboutLinks.isShown("https://t.me/mutify"));
    }
}
