package cobaltumsmp.discordbot.module.versioncheck;

import cobaltumsmp.discordbot.config.ConfigHelper;

import java.util.List;

/**
 * {@link VersionCheckModule} config.
 */
public class Config {
    public static final String MINECRAFT_URL = ConfigHelper.get("VersionCheckerMinecraftUrl",
            "VC_MINECRAFT_URL").getAsSingleOrFail();
    public static final String JIRA_URL = ConfigHelper.get("VersionCheckerJiraUrl",
            "VC_JIRA_URL").getAsSingleOrFail();
    public static final List<Long> CHANNEL_ID_MC_UPDATES = ConfigHelper.getMultipleIdsFromConfig(
            "VersionCheckerMinecraftUpdatesChannel(s)", "CHANNEL_ID_VC_MC");
    public static final List<Long> CHANNEL_ID_JIRA_UPDATES = ConfigHelper.getMultipleIdsFromConfig(
            "VersionCheckerJiraUpdatesChannel(s)", "CHANNEL_ID_VC_JIRA");
    public static final int CHECK_DELAY;
    public static final String SNAPSHOT_ARTICLE_URL_FORMAT =
            ConfigHelper.get("SnapshotArticleUrlFormat",
                    "VC_SNAPSHOT_ARTICLE_URL_FORMAT").getAsSingleOrFail();

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
