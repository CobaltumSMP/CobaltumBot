package cobaltumsmp.discordbot.command;

import cobaltumsmp.discordbot.Roles;
import org.javacord.api.entity.message.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * An interface to provide common methods for all the commands.
 */
public interface Command {
    String name();

    default List<String> aliases() {
        return new ArrayList<>();
    }

    default String[] description() {
        return new String[]{this.name()};
    }

    default boolean useOnGuild() {
        return this.mainGuild();
    }

    default boolean mainGuild() {
        return false;
    }

    default int useArgs() {
        return 0;
    }

    default Roles roleRequired() {
        return Roles.NONE;
    }

    default boolean countAttachmentsAsArgs() {
        return false;
    }

    void execute(Message message, List<String> args);
}
