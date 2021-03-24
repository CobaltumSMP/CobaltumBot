package cobaltumsmp.discordbot.module.versioncheck;

import cobaltumsmp.discordbot.Util;

/**
 * {@link VersionCheckModule} config.
 */
public class Config {
    public static final String MINECRAFT_URL = Util.getEnv("VC_MINECRAFT_URL");
    public static final String JIRA_URL = Util.getEnv("VC_JIRA_URL");
    public static final String CHANNEL_ID_MC_UPDATES = Util.getEnv("CHANNEL_ID_VC_MC");
    public static final String CHANNEL_ID_JIRA_UPDATES = Util.getEnv("CHANNEL_ID_VC_JIRA");
    public static final int CHECK_DELAY;

    static {
        int checkDelay;
        if (!Util.getEnv("VC_CHECK_DELAY").isEmpty()) {
            try {
                checkDelay = Integer.parseInt(Util.getEnv("VC_CHECK_DELAY"));
            } catch (NumberFormatException ignored) {
                checkDelay = 30;
            }
        } else {
            checkDelay = 30;
        }

        CHECK_DELAY = checkDelay;
    }
}
