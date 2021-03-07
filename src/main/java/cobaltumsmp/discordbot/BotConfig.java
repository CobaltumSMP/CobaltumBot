package cobaltumsmp.discordbot;

import java.util.function.Function;

public class BotConfig {
    public static final String PREFIX = System.getenv("BOT_PREFIX");
    public static final long GUILD_ID_MAIN;
    public static final long CHANNEL_ID_BROADCAST;
    public static final long ROLE_ID_DEV;
    public static final long ROLE_ID_STAFF;
    public static final long ROLE_ID_MOD;
    public static final long ROLE_ID_ADMIN;
    public static final long ROLE_ID_OWNER;

    static {
        Function<String, Long> idProvider = s -> {
            long id;
            try {
                id = Long.parseLong(System.getenv(s));
            } catch (NumberFormatException e) {
                id = 0L;
            }

            return id;
        };

        GUILD_ID_MAIN = idProvider.apply("GUILD_ID_MAIN");
        CHANNEL_ID_BROADCAST = idProvider.apply("CHANNEL_ID_BROADCAST");
        ROLE_ID_DEV = idProvider.apply("ROLE_ID_DEV");
        ROLE_ID_STAFF = idProvider.apply("ROLE_ID_STAFF");
        ROLE_ID_MOD = idProvider.apply("ROLE_ID_MOD");
        ROLE_ID_ADMIN = idProvider.apply("ROLE_ID_ADMIN");
        ROLE_ID_OWNER = idProvider.apply("ROLE_ID_OWNER");
    }
}
