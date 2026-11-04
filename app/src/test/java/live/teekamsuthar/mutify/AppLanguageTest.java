package live.teekamsuthar.mutify;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppLanguageTest {

    @Test
    public void noAppLocaleMeansSystemDefault() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""));
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(null));
    }

    @Test
    public void everyOfferedLanguageMapsBackToItself() {
        for (AppLanguage language : AppLanguage.values()) {
            assertEquals(language, AppLanguage.fromTags(language.tag));
        }
    }

    @Test
    public void regionalVariantsMapToTheirLanguage() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTags("de-AT"));
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTags("es-419"));
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.fromTags("pt-PT"));
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en_GB"));
    }

    @Test
    public void onlyTheFirstOfSeveralLocalesCounts() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTags("fr-CA,de"));
    }

    @Test
    public void unshippedLanguageCountsAsSystemDefault() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("ja"));
    }
}
