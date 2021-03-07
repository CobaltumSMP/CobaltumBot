package cobaltumsmp.discordbot.module.ticketsystem;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.module.Module;
import cobaltumsmp.discordbot.module.ticketsystem.command.SetupCommand;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;

import java.util.List;
import java.util.function.Consumer;

public class TicketSystemModule extends Module {
    public static final Logger LOGGER = LogManager.getLogger();
    protected static ObjectContext context;
    protected List<TicketConfig> configs;

    @Override
    public String name() {
        return "Ticket System";
    }

    @Override
    public void init() {
        try {
            ServerRuntime cayenneRuntime = ServerRuntime.builder().addConfig("cayenne-project.xml").build();
            context = cayenneRuntime.newContext();
            LOGGER.info("Connected to database");
        } catch (Exception e) {
            LOGGER.error("Failed to connect to database", e);
            return;
        }

        this.updateConfigs();
        this.loaded = true;
    }

    @Override
    public Consumer<DiscordApi> getInstallFunction() {
        return discordApi -> Main.loadCommand(new SetupCommand(this));
    }

    public void openTicket(TicketConfig ticketConfig) {
        // TODO
    }

    public void updateConfigs() {
        this.configs = ObjectSelect.query(TicketConfig.class).select(context);
    }

    public ObjectContext getContext() {
        return context;
    }
}
