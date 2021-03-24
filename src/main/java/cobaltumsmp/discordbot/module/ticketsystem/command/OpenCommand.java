package cobaltumsmp.discordbot.module.ticketsystem.command;

import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.i18n.I18nUtil;
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
        return new String[]{I18nUtil.key("ticket_system.command.open.description")};
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
            message.getChannel().sendMessage(I18nUtil.key("ticket_system.command.open.no_config"));
            return;
        } else if (this.module.getConfigCount() > 1) {
            message.getChannel().sendMessage(
                    I18nUtil.key("ticket_system.command.open.too_many_configs"));
            return;
        }

        TicketConfig config = this.module.getConfigs().get(0);
        if (Util.isMessageUserAuthorEmpty(message)) {
            Util.unexpectedErrorMessageResponse(message);
            return;
        }

        this.module.openTicket(config, message.getUserAuthor().get());
    }
}
