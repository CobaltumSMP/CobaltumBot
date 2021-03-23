package cobaltumsmp.discordbot.module.versioncheck;

/**
 * {@link VersionCheckModule} config.
 */
public class Config {
    public static final String MINECRAFT_URL = System.getenv("VC_MINECRAFT_URL");
    public static final String JIRA_URL = System.getenv("VC_JIRA_URL");
    public static final String CHANNEL_ID_MC_UPDATES = System.getenv("CHANNEL_ID_VC_MC");
    public static final String CHANNEL_ID_JIRA_UPDATES = System.getenv("CHANNEL_ID_VC_JIRA");
    public static final int CHECK_DELAY;

    static {
        int checkDelay;
        if (System.getenv("VC_CHECK_DELAY") != null && !System.getenv("VC_CHECK_DELAY").isEmpty()) {
            try {
                checkDelay = Integer.parseInt(System.getenv("VC_CHECK_DELAY"));
            } catch (NumberFormatException ignored) {
                checkDelay = 30;
            }
        } else {
            checkDelay = 30;
        }

        CHECK_DELAY = checkDelay;
    }
}
