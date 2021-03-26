package cobaltumsmp.discordbot.module;

import cobaltumsmp.discordbot.command.Command;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;

import javax.annotation.Nullable;
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

    public @Nullable Consumer<DiscordApi> getInstallFunction() {
        return null;
    }

    public final boolean isEnabled() {
        return this.enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public final boolean isLoaded() {
        return this.loaded;
    }

    public final String getId() {
        return this.name().toLowerCase().replace(" ", "_");
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
                channel.sendMessage(I18nUtil.key("module.command.not_enabled"));
            } else if (!this.module.isLoaded()) {
                channel.sendMessage(I18nUtil.key("module.command.not_loaded"));
            }

            return !this.module.isEnabled() || !this.module.isLoaded();
        }
    }
}
