package cobaltumsmp.discordbot;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Roles for the main guild.
 */
public enum Roles {
    NONE(BotConfig.GUILD_ID_MAIN), // Everyone role has the same id as the server
    DEV(BotConfig.ROLE_ID_DEV),
    STAFF(BotConfig.ROLE_ID_STAFF),
    MOD(BotConfig.ROLE_ID_MOD),
    ADMIN(BotConfig.ROLE_ID_ADMIN),
    OWNER(BotConfig.ROLE_ID_OWNER);

    private static final List<Roles> noNullValues = Arrays.asList(values());
    private final Role role;

    Roles(long id) {
        this.role = getRoleById(id);
    }

    /**
     * Check if the given user has the same or a higher role than the provided.
     *
     * @param user the user to check.
     * @param role the role that the user should have as minimum.
     * @return if the user has the same or a higher role.
     */
    public static boolean checkRoles(@Nonnull User user, @Nonnull Roles role) {
        if (role == Roles.DEV) {
            return true;
        }

        Optional<Server> mainServer = Main.getApi().getServerById(BotConfig.GUILD_ID_MAIN);
        if (mainServer.isEmpty()) {
            Main.LOGGER.warn("The main guild ID is invalid. Some commands may not work.");
            return false;
        }

        List<Role> roles = user.getRoles(mainServer.get());

        // Iterate from the lowest to higher role so having a higher role than the
        // requested one returns true as well
        for (int i = role.ordinal(); i < noNullValues.size(); ++i) {
            if (roles.contains(noNullValues.get(i).role)) {
                return true;
            }
        }

        return false;
    }

    private static @Nullable Role getRoleById(long id) {
        return Main.getApi().getRoleById(id).orElse(null);
    }

    static {
        if (noNullValues.removeIf(roles -> roles.role == null)) {
            Main.LOGGER.warn(
                    "One (or more) of the role IDs is invalid. Some commands may not work.");
        }
    }
}
