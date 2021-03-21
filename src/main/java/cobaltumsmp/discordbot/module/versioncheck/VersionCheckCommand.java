package cobaltumsmp.discordbot.module.versioncheck;

import cobaltumsmp.discordbot.Roles;
import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.module.Module;
import org.javacord.api.entity.message.Message;

import java.util.List;

/**
 * Execute a version check.
 */
public class VersionCheckCommand extends Module.ModuleCommand<VersionCheckModule> {
    public VersionCheckCommand(VersionCheckModule module) {
        super(module);
    }

    @Override
    public String name() {
        return "versioncheck";
    }

    @Override
    public String[] description() {
        return new String[]{"Execute a version check."};
    }

    @Override
    public Roles roleRequired() {
        return Roles.MOD;
    }

    @Override
    public void execute(Message message, List<String> args) {
        if (!this.cantExecute(message.getChannel())) {
            return;
        }

        VersionCheckModule.LOGGER.debug(
                "Running requested version check task from command (requested by {}).",
                message.getAuthor().getDiscriminatedName());

        try {
            this.module.checkUpdates();
            message.getChannel().sendMessage("Version check task ran successfully.");
        } catch (Exception e) {
            VersionCheckModule.LOGGER.error(
                    "Encountered an error while running the requested version check task.", e);
            Util.unexpectedErrorMessageResponse(message);
        }
    }
}
