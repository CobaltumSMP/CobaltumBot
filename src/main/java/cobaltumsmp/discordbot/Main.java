package cobaltumsmp.discordbot;

import cobaltumsmp.discordbot.command.BroadcastCommand;
import cobaltumsmp.discordbot.command.Command;
import cobaltumsmp.discordbot.command.EchoCommand;
import cobaltumsmp.discordbot.command.HelpCommand;
import cobaltumsmp.discordbot.command.PingCommand;
import cobaltumsmp.discordbot.command.SetPresenceCommand;
import cobaltumsmp.discordbot.event.MessageListener;
import cobaltumsmp.discordbot.i18n.Language;
import cobaltumsmp.discordbot.module.Module;
import cobaltumsmp.discordbot.module.RconModule;
import cobaltumsmp.discordbot.module.ticketsystem.TicketSystemModule;
import cobaltumsmp.discordbot.module.versioncheck.VersionCheckModule;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.listener.GloballyAttachableListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main class for the CobaltumBot.
 */
public class Main {
    public static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();
    public static final ListeningExecutorService SERVICE =
            MoreExecutors.listeningDecorator(THREAD_POOL);
    /**
     * The commands, stored with command name as key and the command as value.
     */
    public static final Map<String, Command> COMMANDS = new TreeMap<>();
    private static final List<GloballyAttachableListener> LISTENERS = new ArrayList<>();
    public static final Map<String, Module> MODULES = new TreeMap<>();
    /**
     * A list of channels that can't be used for commands.
     */
    public static final List<Long> EXCLUSIVE_CHANNELS = new ArrayList<>();
    private static DiscordApi api;

    /**
     * Entrypoint for the bot.
     */
    public static void main(String[] args) {
        String token = "";
        try {
            String tokenEnv = System.getenv("DISCORD_TOKEN");
            token = tokenEnv != null && !tokenEnv.equals("") ? tokenEnv : args[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.error("You must provide a discord token either with an argument or a "
                            + "system variable!");
            System.exit(-1);
        }

        if (BotConfig.PREFIX == null || BotConfig.PREFIX.equals("")) {
            LOGGER.error("Please set a valid prefix with the environment variable 'PREFIX'");
            System.exit(-1);
        }

        loadLanguage(BotConfig.LOCALE);

        loadCommands();
        int eventCount = loadEventListeners();
        LOGGER.info("Loaded {} commands and {} events", COMMANDS.size(), eventCount);

        api = new DiscordApiBuilder().setToken(token).login()
                .exceptionally(throwable -> {
                    LOGGER.error("There was an error trying to login at Discord", throwable);
                    throw new RuntimeException(throwable);
                })
                .thenApply(discordApi -> {
                    LOGGER.info("CobaltumBot is ready at user {}. Using prefix '{}'",
                            discordApi.getYourself().getDiscriminatedName(), BotConfig.PREFIX);
                    discordApi.updateActivity(BotConfig.PREFIX + "ping");
                    installModules(discordApi);
                    return discordApi;
                })
                .join();

        initModules();
        applyEventListeners();
    }

    private static void loadLanguage(Locale locale) {
        String defaultFailed
                = "Failed to load default language! Translations won't work as expected";
        String otherFailed = String.format(
                "Failed to load language '%s'. Trying to load default language",
                locale.toLanguageTag());

        try {
            Language lang = Language.load(locale.toLanguageTag());

            if (lang == null) {
                if (locale == Locale.US) {
                    LOGGER.warn(defaultFailed);
                } else {
                    // Retry by loading default language instead
                    LOGGER.warn(otherFailed);
                    loadLanguage(Locale.US);
                }
            }
        } catch (IOException e) {
            if (locale == Locale.US) {
                LOGGER.error(defaultFailed, e);
            } else {
                // Retry by loading default language instead
                LOGGER.error(otherFailed, e);
                loadLanguage(Locale.US);
            }
        }
    }

    private static void loadCommands() {
        loadCommand(new BroadcastCommand());
        loadCommand(new EchoCommand());
        loadCommand(new HelpCommand());
        loadCommand(new PingCommand());
        loadCommand(new SetPresenceCommand());
    }

    public static void loadCommand(Command command) {
        COMMANDS.put(command.name(), command);
    }

    private static int loadEventListeners() {
        LISTENERS.add(new MessageListener());
        return LISTENERS.size();
    }

    private static void applyEventListeners() {
        for (GloballyAttachableListener listener : LISTENERS) {
            api.addListener(listener);
        }
    }

    private static void installModules(DiscordApi discordApi) {
        installModule(new VersionCheckModule(), discordApi);
        installModule(new RconModule(), discordApi);
        installModule(new TicketSystemModule(), discordApi);
    }

    private static void installModule(Module module, DiscordApi discordApi) {
        disableModuleFromEnv(module);
        if (!module.isEnabled()) {
            return;
        }

        if (module.getInstallFunction() != null) {
            module.getInstallFunction().accept(discordApi);
        }

        MODULES.put(module.name(), module);
        LOGGER.debug("Installed module {}", module.name());
    }

    private static void initModules() {
        for (Module module : MODULES.values()) {
            SERVICE.submit(module::init);
        }
    }

    private static void disableModuleFromEnv(Module module) {
        String key = "MODULE_" + module.getId().toUpperCase() + "_ENABLED";
        if (!Util.isSystemEnvEmpty(key) && System.getenv(key).equalsIgnoreCase("false")) {
            module.setEnabled(false);
            LOGGER.info("Module '{}' has been disabled via env variable.", module.name());
        }
    }

    public static void addEventListener(GloballyAttachableListener listener) {
        api.addListener(listener);
    }

    public static DiscordApi getApi() {
        return api;
    }
}
