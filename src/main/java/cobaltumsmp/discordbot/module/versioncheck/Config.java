package cobaltumsmp.discordbot.module.versioncheck;

public class Config {
    public static final String MINECRAFT_URL = System.getenv("VC_MINECRAFT_URL");
    public static final String JIRA_URL = System.getenv("VC_JIRA_URL");
    public static final String CHANNEL_ID_MC_UPDATES = System.getenv("CHANNEL_ID_VC_MC");
    public static final String CHANNEL_ID_JIRA_UPDATES = System.getenv("CHANNEL_ID_VC_JIRA");
}
