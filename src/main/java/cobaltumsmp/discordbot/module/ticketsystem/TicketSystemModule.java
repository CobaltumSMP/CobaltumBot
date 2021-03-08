package cobaltumsmp.discordbot.module.ticketsystem;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.module.Module;
import cobaltumsmp.discordbot.module.ticketsystem.command.OpenCommand;
import cobaltumsmp.discordbot.module.ticketsystem.command.SetupCommand;
import com.google.common.base.Strings;
import com.vdurmont.emoji.EmojiParser;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.ServerTextChannelBuilder;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A module that provides support to users through tickets.
 */
public class TicketSystemModule extends Module {
    public static final Logger LOGGER = LogManager.getLogger();
    protected static ObjectContext context;
    protected List<TicketConfig> configs;
    protected List<Ticket> tickets;

    @Override
    public String name() {
        return "Ticket System";
    }

    @Override
    public void init() {
        try {
            String env = System.getenv("CAYENNE_CONFIG_FILE");
            ServerRuntime cayenneRuntime = ServerRuntime.builder()
                    .addConfig(env != null && !env.equals("") ? env : "cayenne-project.xml")
                    .build();
            context = cayenneRuntime.newContext();
            LOGGER.info("Connected to database");
        } catch (Exception e) {
            LOGGER.error("Failed to connect to database", e);
            return;
        }

        this.updateConfigs();
        this.updateTickets();
        this.loaded = true;
    }

    @Override
    public Consumer<DiscordApi> getInstallFunction() {
        return discordApi -> {
            Main.loadCommand(new SetupCommand(this));
            Main.loadCommand(new OpenCommand(this));
        };
    }

    /**
     * Open a new {@link Ticket}.
     *
     * @param ticketConfig the configuration to open the ticket under.
     * @param owner the owner of the ticket.
     */
    public void openTicket(TicketConfig ticketConfig, User owner) {
        LOGGER.info("Opening ticket...");
        DiscordApi api = Main.getApi();
        Optional<ChannelCategory> channelCategoryOptional;
        if ((channelCategoryOptional = api
                .getChannelCategoryById(ticketConfig.getTicketCategoryId())).isEmpty()) {
            LOGGER.warn("The ticket category with ID '{}' was not found.",
                    ticketConfig.getTicketCategoryId());
            return;
        }

        ChannelCategory ticketCategory = channelCategoryOptional.get();
        ServerTextChannel ticketChannel;
        try {
            ticketChannel = new ServerTextChannelBuilder(ticketCategory.getServer())
                    .setCategory(ticketCategory)
                    .setName("ticket-"
                            + Strings.padStart(Integer.toString(this.getTicketCount()), 4, '0'))
                    .create().join();
        } catch (Exception e) {
            LOGGER.error("There was an error trying to create a ticket channel.", e);
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Hello " + owner.getMentionTag()
                        + ", support will be with you shortly."

                        + " In the meantime, please provide as much information as possible.\n"
                        + "React with " + EmojiParser.parseToUnicode(":lock:")
                        + " to close the ticket.");
        Message botMsg;
        try {
            botMsg = new MessageBuilder().setEmbed(embed).send(ticketChannel).join();
            botMsg.addReaction(EmojiParser.parseToUnicode(":lock:"));
        } catch (Exception e) {
            LOGGER.error("There was an error trying to send the ticket embed.", e);
            return;
        }

        Ticket ticket = context.newObject(Ticket.class);
        ticket.setBotMsgId(botMsg.getId());
        ticket.setChannelId(ticketChannel.getId());
        ticket.setClosed(false);
        ticket.setOwnerId(owner.getId());
        ticket.setTicketId(ticketConfig.getTickets().size());
        ticket.setTicketConfig(ticketConfig);
        ticketConfig.addToTickets(ticket);

        context.commitChanges();
        this.updateTickets();
        this.updateConfigs();
        LOGGER.info("Ticket ID '{}' created", this.getTicketCount() - 1);

        ticketChannel.createUpdater().setName("ticket-"
                + Strings.padStart(Integer.toString(ticket.getTicketId()), 4, '0')).update();
    }

    public void updateConfigs() {
        this.configs = ObjectSelect.query(TicketConfig.class).select(context);
    }

    public void updateTickets() {
        this.tickets = ObjectSelect.query(Ticket.class).select(context);
    }

    public ObjectContext getContext() {
        return context;
    }

    public List<TicketConfig> getConfigs() {
        return this.configs;
    }

    public List<Ticket> getTickets() {
        return this.tickets;
    }

    public int getConfigCount() {
        return this.getConfigs().size();
    }

    public int getTicketCount() {
        return this.getTickets().size();
    }
}
