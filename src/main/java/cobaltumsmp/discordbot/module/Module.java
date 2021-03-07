package cobaltumsmp.discordbot.module;

import cobaltumsmp.discordbot.command.Command;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;

import java.util.function.Consumer;

/**
 * An (ideally) independent module with its own commands and events.
 * Provides common methods and fields for all modules.
 */
public abstract class Module {
    protected boolean enabled = true;
    protected boolean loaded = false;

    public abstract String name();

    public abstract void init();

    public Consumer<DiscordApi> getInstallFunction() {
        return null;
    }

    public final boolean isEnabled() {
        return this.enabled;
    }

    public final boolean isLoaded() {
        return this.loaded;
    }

    /**
     * A module dependent command.
     * Provides common methods and fields for all module dependent commands.
     *
     * @param <M> the module this command depends on.
     */
    public abstract static class ModuleCommand<M extends Module> implements Command {
        protected final M module;

        public ModuleCommand(M module) {
            this.module = module;
        }

        /**
         * Checks the module this command depends on.
         *
         * @param channel the channel to send feedback to.
         * @return if the module isn't loaded or enabled.
         */
        public final boolean cantExecute(TextChannel channel) {
            if (!this.module.isEnabled()) {
                channel.sendMessage("The module this command depends on isn't enabled.");
            } else if (!this.module.isLoaded()) {
                channel.sendMessage("The module this command depends on isn't loaded.");
            }

            return !this.module.isEnabled() || !this.module.isLoaded();
        }
    }
}
