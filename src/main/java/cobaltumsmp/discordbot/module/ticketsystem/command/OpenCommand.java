package cobaltumsmp.discordbot.module.ticketsystem.command;

import cobaltumsmp.discordbot.module.Module;
import cobaltumsmp.discordbot.module.ticketsystem.TicketConfig;
import cobaltumsmp.discordbot.module.ticketsystem.TicketSystemModule;
import org.javacord.api.entity.message.Message;

import java.util.Arrays;
import java.util.List;

/**
 * Open a ticket.
 *
 * <p>Usage: {@code open}</p>
 */
public class OpenCommand extends Module.ModuleCommand<TicketSystemModule> {
    public OpenCommand(TicketSystemModule module) {
        super(module);
    }

    @Override
    public String name() {
        return "open";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("openticket", "ticketopen");
    }

    @Override
    public String[] description() {
        return new String[]{"Open a ticket."};
    }

    @Override
    public boolean mainGuild() {
        return true;
    }

    @Override
    public void execute(Message message, List<String> args) {
        if (this.cantExecute(message.getChannel())) {
            return;
        }

        if (this.module.getConfigCount() <= 0) {
            message.getChannel().sendMessage(
                    "There is no valid ticket configuration. You can't do this.");
            return;
        } else if (this.module.getConfigCount() > 1) {
            message.getChannel().sendMessage(
                    "There is more than one ticket configuration. You can't do this.");
            return;
        }

        TicketConfig config = this.module.getConfigs().get(0);
        if (message.getUserAuthor().isEmpty()) {
            message.getChannel().sendMessage("There was an unexpected error.");
            TicketSystemModule.LOGGER.error(new IllegalStateException(
                    "Message#getUserAuthor was empty"));
            return;
        }

        this.module.openTicket(config, message.getUserAuthor().get());
    }
}
