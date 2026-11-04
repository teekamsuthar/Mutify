package live.teekamsuthar.mutify;

/**
 * Which of the About screen's optional links are shown. The Telegram and Privacy Policy URLs are
 * string resources the author fills in later; until then their entries stay hidden, so no entry
 * leads nowhere.
 */
final class AboutLinks {

    private AboutLinks() {
    }

    /** An optional link is shown once its URL is filled in. */
    static boolean isShown(String url) {
        return url != null && !url.trim().isEmpty();
    }
}
