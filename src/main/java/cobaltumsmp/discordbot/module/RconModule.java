package cobaltumsmp.discordbot.module;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import me.catcoder.jrcon.JRcon;
import me.catcoder.jrcon.JRconSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * A module to connect via RCON to a Minecraft server.
 */
public class RconModule extends Module {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(Config.HOST,
            Config.PORT);
    private static final EventLoopGroup EVENT_LOOPS = JRcon.newEventloops(3);
    private final Bootstrap rconBootstrap;
    private JRconSession session;
    private TextChannel rconChannel;
    private boolean connected = false;
    private boolean authenticated = false;

    public RconModule() {
        this.rconBootstrap = JRcon.newBootstrap(SERVER_ADDRESS, EVENT_LOOPS);
    }

    @Override
    public String name() {
        return "RCON";
    }

    @Override
    public void init() {
        Channel rconChannel = Main.getApi().getChannelById(Config.CHANNEL_ID_RCON).orElse(null);

        if (rconChannel == null || rconChannel.getType() != ChannelType.SERVER_TEXT_CHANNEL
                || rconChannel.asTextChannel().isEmpty()) {
            LOGGER.error("The configured channel is invalid.");
            return;
        }

        this.rconChannel = rconChannel.asTextChannel().get();

        this.connect().addListener(future -> {
            if (future.isSuccess()) {
                LOGGER.info("RCON connected at {}:{}", Config.HOST, Config.PORT);
                this.connected = true;

                this.session = JRcon.newSession((ChannelFuture) future);
                this.session.authenticate(Config.PASS).get();

                this.authenticated = this.session.isAuthenticated();
            } else {
                LOGGER.error("There was an error trying to establish the RCON connection.",
                        future.cause());
            }
        });
        this.loaded = true;
    }

    @Override
    public Consumer<DiscordApi> getInstallFunction() {
        return discordApi -> {
            discordApi.addListener(new MessageListener());
            Main.loadCommand(new RconConnectCommand(this));
        };
    }

    private ChannelFuture connect() {
        return this.rconBootstrap.connect();
    }

    static class Config {
        public static final String CHANNEL_ID_RCON = System.getenv("CHANNEL_ID_RCON");
        public static final String HOST = System.getenv("RCON_HOST");
        public static final String PASS = System.getenv("RCON_PASS");
        public static final int PORT;

        static {
            int port;
            try {
                port = Integer.parseInt(System.getenv("RCON_PORT"));
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid port provided.", e);
                port = 25575;
            }
            PORT = port;
        }
    }

    class RconConnectCommand extends ModuleCommand<RconModule> {
        public RconConnectCommand(RconModule module) {
            super(module);
        }

        @Override
        public String name() {
            return "rconconnect";
        }

        @Override
        public List<String> aliases() {
            return Arrays.asList("connectrcon", "rcon");
        }

        @Override
        public String[] description() {
            return new String[]{"Try to reconnect the RCON connection if it failed before."};
        }

        @Override
        public boolean useOnGuild() {
            return true;
        }

        @Override
        public Roles roleRequired() {
            return Roles.ADMIN;
        }

        @Override
        public void execute(Message message, List<String> args) {
            if (this.cantExecute(message.getChannel())) {
                return;
            }

            if (RconModule.this.authenticated || RconModule.this.connected) {
                message.getChannel().sendMessage("RCON is already connected.");
                return;
            }

            LOGGER.info("Trying to establish connection via command.");
            message.getChannel().sendMessage(String.format("Connecting RCON at channel <#%s>",
                    RconModule.this.rconChannel.getId()));
            RconModule.this.connect().addListener(future -> {
                if (future.isSuccess()) {
                    LOGGER.info("RCON connected at {}:{}", Config.HOST, Config.PORT);
                    message.getChannel().sendMessage(String.format(
                            "RCON connected at channel <#%s>",
                            RconModule.this.rconChannel.getId()));
                } else {
                    LOGGER.error("There was an error trying to establish the RCON connection.",
                            future.cause());
                    message.getChannel().sendMessage(
                            "There was an error trying to establish the RCON connection.");
                }
            });
        }
    }

    class MessageListener implements MessageCreateListener {
        @Override
        public void onMessageCreate(MessageCreateEvent event) {
            if (event.getMessageAuthor().isBotUser() || event.getMessageAuthor().isWebhook()) {
                return;
            }

            if (!event.getChannel().getIdAsString().equals(Config.CHANNEL_ID_RCON)) {
                return;
            }

            this.executeRconCommand(event.getMessage());
        }

        private void executeRconCommand(Message message) {
            if (!message.getContent().startsWith("/")) {
                message.delete("Can't send messages in RCON channel.");
                return;
            }

            if (!RconModule.this.isLoaded()) {
                message.getChannel().sendMessage("The module this depends on is not loaded.");
                return;
            }

            if (!Roles.checkRoles(message.getUserAuthor().get(), Roles.ADMIN)) {
                message.getChannel().sendMessage("You don't have permission to do that.");
                return;
            }

            if (!RconModule.this.connected) {
                message.getChannel().sendMessage(
                        "The RCON connection hasn't been established."
                                + " Please try again after running the `rconconnect` command");
                return;
            }

            if (!RconModule.this.authenticated) {
                message.getChannel().sendMessage(
                        "The RCON connection wasn't authenticated."
                                + " Please fix the credentials, restart the bot"
                                + " and try again after restarting the bot.");
                return;
            }

            try {
                String response = RconModule.this.session.executeCommand(message.getContent())
                        .join();
                message.getChannel().sendMessage(String.format("Response:\n```\n%s\n```",
                        response));
                LOGGER.debug("{} executed command '{}', and received response '{}'",
                        message.getUserAuthor().get().getDiscriminatedName(), message.getChannel(),
                        response);
            } catch (Exception e) {
                LOGGER.error(e);
                message.getChannel().sendMessage(
                        "There was an error trying to execute that command");
            }
        }
    }
}
