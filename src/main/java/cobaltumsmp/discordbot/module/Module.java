package cobaltumsmp.discordbot.module;

import cobaltumsmp.discordbot.command.Command;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.Message;

import java.util.function.Consumer;

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

    public static abstract class ModuleCommand<M extends Module> implements Command {
        protected final M module;

        public ModuleCommand(M module) {
            this.module = module;
        }

        public final boolean cantExecute(Message message) {
            if (!this.module.isEnabled()) {
                message.getChannel().sendMessage("The module this command depends on isn't enabled.");
            } else if (!this.module.isLoaded()) {
                message.getChannel().sendMessage("The module this command depends on isn't loaded.");
            }

            return !this.module.isEnabled() || !this.module.isLoaded();
        }
    }
}
