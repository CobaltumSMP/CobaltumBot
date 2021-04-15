package cobaltumsmp.discordbot.module.versioncheck;

import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.config.ConfigHelper;

/**
 * {@link VersionCheckModule} config.
 */
public class Config {
    public static final String MINECRAFT_URL = ConfigHelper.get("VersionCheckerMinecraftUrl",
            "VC_MINECRAFT_URL").getAsSingleOrFail();
    public static final String JIRA_URL = ConfigHelper.get("VersionCheckerJiraUrl",
            "VC_JIRA_URL").getAsSingleOrFail();
    public static final String CHANNEL_ID_MC_UPDATES = Util.getEnv("CHANNEL_ID_VC_MC");
    public static final String CHANNEL_ID_JIRA_UPDATES = Util.getEnv("CHANNEL_ID_VC_JIRA");
    public static final int CHECK_DELAY;

    static {
        int checkDelay;
        if (!ConfigHelper.get("VersionCheckerDelay", "VC_CHECK_DELAY").getAsSingleOrFail()
                .isEmpty()) {
            try {
                checkDelay = Integer.parseInt(ConfigHelper.get("VersionCheckerDelay",
                        "VC_CHECK_DELAY").getAsSingleOrFail());
            } catch (NumberFormatException ignored) {
                checkDelay = 30;
            }
        } else {
            checkDelay = 30;
        }

        CHECK_DELAY = checkDelay;
    }
}
