package cobaltumsmp.discordbot;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;

import java.util.List;

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
    public static boolean checkRoles(User user, Roles role) {
        if (role == Roles.DEV) {
            return true;
        }

        List<Role> roles = user.getRoles(Main.getApi().getServerById(BotConfig.GUILD_ID_MAIN)
                .get());

        // Iterate from the lowest to higher role so having a higher role than the
        // requested one returns true as well
        for (int i = role.ordinal(); i < Roles.values().length; ++i) {
            if (roles.contains(Roles.values()[i].role)) {
                return true;
            }
        }

        return false;
    }

    private static Role getRoleById(long id) {
        return Main.getApi().getRoleById(id).get();
    }
}
