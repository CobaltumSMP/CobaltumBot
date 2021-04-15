package cobaltumsmp.discordbot.module;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.config.ConfigHelper;
import cobaltumsmp.discordbot.i18n.I18nUtil;
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

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * A module to connect via RCON to a Minecraft server.
 */
public class RconModule extends Module {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final EventLoopGroup EVENT_LOOPS = JRcon.newEventloops(3);
    private Bootstrap rconBootstrap;
    private JRconSession session;
    private TextChannel rconChannel;
    private boolean connected = false;
    private boolean authenticated = false;

    @Override
    public String name() {
        return "RCON";
    }

    @Override
    public void init() {
        InetSocketAddress serverAddress = new InetSocketAddress(Config.HOST, Config.PORT);
        this.rconBootstrap = JRcon.newBootstrap(serverAddress, EVENT_LOOPS);

        Channel rconChannel = Main.getApi().getChannelById(Config.CHANNEL_ID_RCON).orElse(null);

        if (rconChannel == null || rconChannel.getType() != ChannelType.SERVER_TEXT_CHANNEL
                || rconChannel.asTextChannel().isEmpty()) {
            LOGGER.error("The configured channel is invalid.");
            return;
        }

        this.rconChannel = rconChannel.asTextChannel().get();
        Main.EXCLUSIVE_CHANNELS.add(this.rconChannel.getId());

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
    public @Nullable Consumer<DiscordApi> getInstallFunction() {
        return discordApi -> {
            discordApi.addListener(new MessageListener());
            Main.loadCommand(new RconConnectCommand(this));
        };
    }

    private ChannelFuture connect() {
        return this.rconBootstrap.connect();
    }

    static class Config {
        public static final long CHANNEL_ID_RCON = ConfigHelper.getIdFromConfig("RconChannelId",
                "CHANNEL_ID_RCON");
        public static final String HOST = ConfigHelper.get("RconHost", "RCON_HOST")
                .getAsSingleOrFail();
        public static final String PASS = ConfigHelper.get("RconPass", "RCON_PASS")
                .getAsSingleOrFail();
        public static final int PORT;

        static {
            int port;
            try {
                port = Integer.parseInt(ConfigHelper.get("RconPort", "RCON_PORT")
                        .getAsSingleOrFail());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid port provided.", e);
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
            return new String[]{I18nUtil.key("rcon.command.rconconnect.description")};
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
                message.getChannel().sendMessage(I18nUtil.key(
                        "rcon.command.rconconnnect.already_connected"));
                return;
            }

            LOGGER.info("Trying to establish connection via command.");
            message.getChannel().sendMessage(I18nUtil.formatKey(
                    "rcon.command.rconconnnect.connecting",
                    "<#" + RconModule.this.rconChannel.getId() + ">"));
            RconModule.this.connect().addListener(future -> {
                if (future.isSuccess()) {
                    LOGGER.info("RCON connected at {}:{}", Config.HOST, Config.PORT);
                    message.getChannel().sendMessage(I18nUtil.formatKey(
                            "rcon.command.rconconnnect.connected",
                            "<#" + RconModule.this.rconChannel.getId() + ">"));
                } else {
                    LOGGER.error("There was an error trying to establish the RCON connection.",
                            future.cause());
                    message.getChannel().sendMessage(
                            I18nUtil.key("rcon.command.rconconnnect.error"));
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

            if (event.getChannel().getId() != Config.CHANNEL_ID_RCON) {
                return;
            }

            this.executeRconCommand(event.getMessage());
        }

        private void executeRconCommand(Message message) {
            if (!message.getContent().startsWith("/")) {
                message.delete(I18nUtil.key("rcon.cant_send"));
                return;
            }

            if (!RconModule.this.isEnabled()) {
                message.getChannel().sendMessage(I18nUtil.key("module.not_enabled"));
                return;
            }

            if (!RconModule.this.isLoaded()) {
                message.getChannel().sendMessage(I18nUtil.key("module.not_loaded"));
                return;
            }

            if (Util.isMessageUserAuthorEmpty(message)) {
                Util.unexpectedErrorMessageResponse(message);
                return;
            }

            if (!Roles.checkRoles(message.getUserAuthor().get(), Roles.ADMIN)) {
                message.getChannel().sendMessage(I18nUtil.key("command.no_permission"));
                return;
            }

            if (!RconModule.this.connected) {
                message.getChannel().sendMessage(I18nUtil.key("rcon.not_connected"));
                return;
            }

            if (!RconModule.this.authenticated) {
                message.getChannel().sendMessage(I18nUtil.key("rcon.not_authenticated"));
                return;
            }

            try {
                String response = RconModule.this.session.executeCommand(message.getContent())
                        .join();
                message.getChannel().sendMessage(I18nUtil.formatKey("rcon.response",
                        response));
                LOGGER.debug("{} executed command '{}', and received response '{}'",
                        message.getUserAuthor().get().getDiscriminatedName(), message.getChannel(),
                        response);
            } catch (Exception e) {
                LOGGER.error("There was an error trying to execute '{}'", message.getContent(), e);
                message.getChannel().sendMessage(I18nUtil.key("rcon.error"));
            }
        }
    }
}
