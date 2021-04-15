package cobaltumsmp.discordbot.module.versioncheck;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import cobaltumsmp.discordbot.module.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A module that constantly checks the configured URLs ({@link Config#JIRA_URL}
 * and {@link Config#MINECRAFT_URL}) for new versions.
 */
public class VersionCheckModule extends Module {
    protected static final Logger LOGGER = LogManager.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ScheduledExecutorService THREAD_POOL = Executors.newScheduledThreadPool(3,
            new ThreadFactoryBuilder().setNameFormat("version-checker-thread-%d").build());
    private final CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault();
    private final ArrayList<TextChannel> mcUpdatesChannels = new ArrayList<>();
    private final ArrayList<TextChannel> jiraUpdatesChannels = new ArrayList<>();
    private ArrayList<MinecraftObjects.Version> minecraftVersions;
    private ArrayList<JiraObjects.Version> jiraVersions;
    private MinecraftObjects.Response lastSuccessfulMcResponse;
    private JiraObjects.Response lastSuccessfulJiraResponse;
    private boolean checking = false;

    @Override
    public String name() {
        return "Version checker";
    }

    @Override
    public void init() {
        Optional<Channel> chn;
        Channel chn1;

        // Get the MC updates channel(s)
        for (Long id : Config.CHANNEL_ID_MC_UPDATES) {
            if (Util.isChannelByIdEmpty((chn = Main.getApi().getChannelById(id)))
                    || Util.isTextChannelEmpty((chn1 = chn.get()))) {
                LOGGER.warn("One of the Minecraft updates channels ('{}') is invalid.", id);
            } else {
                this.mcUpdatesChannels.add(chn1.asTextChannel().get());
            }
        }

        // Get the Jira updates channel(s)
        for (Long id : Config.CHANNEL_ID_JIRA_UPDATES) {
            if (Util.isChannelByIdEmpty((chn = Main.getApi().getChannelById(id)))
                    || Util.isTextChannelEmpty((chn1 = chn.get()))) {
                LOGGER.warn("One of the Jira updates channels ('{}') is invalid.", id);
            } else {
                this.jiraUpdatesChannels.add(chn1.asTextChannel().get());
            }
        }

        this.checking = true;

        LOGGER.info("Fetching initial data.");
        httpClient.start();

        this.minecraftVersions = this.getMinecraftVersions();
        this.jiraVersions = this.getJiraVersions();
        LOGGER.info("Minecraft | Total versions: {}", this.minecraftVersions.size());
        LOGGER.info("Jira      | Total versions: {}", this.jiraVersions.size());

        this.checking = false;

        LOGGER.debug("Scheduling version check task.");

        THREAD_POOL.scheduleAtFixedRate(() -> {
            LOGGER.debug("Running scheduled version check task.");

            try {
                this.checkUpdates();
            } catch (Exception e) {
                LOGGER.error(
                        "Encountered an error while running the scheduled version check task.", e);
            }
        }, Config.CHECK_DELAY / 2, Config.CHECK_DELAY, TimeUnit.SECONDS);

        LOGGER.debug("Scheduled version check task with {} seconds of delay.", Config.CHECK_DELAY);

        this.loaded = true;
        LOGGER.info("Module ready.");
    }

    @Override
    public @Nullable Consumer<DiscordApi> getInstallFunction() {
        return discordApi -> Main.loadCommand(new VersionCheckCommand(this));
    }

    protected void checkUpdates() {
        if (this.checking) {
            LOGGER.warn("A version check is already running! Skipping version check.");
            return;
        }

        this.checking = true;

        try {
            MinecraftObjects.Version mcVersion = this.checkMinecraftUpdates();
            if (mcVersion != null && !this.mcUpdatesChannels.isEmpty()) {
                String url = null;
                String snapshotUrlFormat = Config.SNAPSHOT_ARTICLE_URL_FORMAT;
                if (mcVersion.type.equals("snapshot") && !snapshotUrlFormat.isEmpty()) {
                    url = String.format(snapshotUrlFormat, mcVersion.id);
                }

                String msg = I18nUtil.formatKey("version_checker.new_mc_version",
                        mcVersion.type, mcVersion.id);
                for (TextChannel chn : this.mcUpdatesChannels) {
                    chn.sendMessage(msg + (url != null ? "\n" + url : ""));
                }
            }

            JiraObjects.Version jiraVersion = this.checkJiraUpdates();
            if (jiraVersion != null && !this.jiraUpdatesChannels.isEmpty()) {
                String msg = I18nUtil.formatKey("version_checker.new_jira_version",
                        jiraVersion.name);
                for (TextChannel chn : this.jiraUpdatesChannels) {
                    chn.sendMessage(msg);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Found uncaught exception while checking updates", e);
        }

        this.checking = false;
    }

    private MinecraftObjects.Version checkMinecraftUpdates() {
        LOGGER.debug("Checking for Minecraft updates.");

        ArrayList<MinecraftObjects.Version> versions = this.getMinecraftVersions();
        Optional<MinecraftObjects.Version> newVersion = findMismatch(this.minecraftVersions,
                versions);

        if (newVersion.isEmpty()) {
            LOGGER.debug("Minecraft | New version: N/A");
            LOGGER.debug("Minecraft | Total versions: {}", versions.size());
        } else {
            LOGGER.info("Minecraft | New version: {}", newVersion.get().id);
            LOGGER.info("Minecraft | Total versions: {}", versions.size());
        }

        this.minecraftVersions = versions;

        return newVersion.orElse(null);
    }

    private JiraObjects.Version checkJiraUpdates() {
        LOGGER.debug("Checking for Jira updates.");

        ArrayList<JiraObjects.Version> versions = this.getJiraVersions();
        Optional<JiraObjects.Version> newVersion = findMismatch(this.jiraVersions, versions);

        if (newVersion.isEmpty()) {
            LOGGER.debug("Jira      | New version: N/A");
            LOGGER.debug("Jira      | Total versions: {}", versions.size());
        } else {
            LOGGER.info("Jira      | New version: {}", newVersion.get().name);
            LOGGER.info("Jira      | Total versions: {}", versions.size());
        }

        this.jiraVersions = versions;

        return newVersion.orElse(null);
    }

    private ArrayList<MinecraftObjects.Version> getMinecraftVersions() {
        try {
            MinecraftObjects.Response response = this.getMinecraftResponse();

            if (response == null) {
                throw new IllegalStateException("The Minecraft response is null");
            } else if (response.versions == null) {
                throw new IllegalStateException("The Minecraft response contains no versions");
            }

            return new ArrayList<>(response.versions);
        } catch (ExecutionException | InterruptedException | IOException e) {
            LOGGER.error("There was an error getting the Minecraft versions.", e);
            return new ArrayList<>();
        }
    }

    private ArrayList<JiraObjects.Version> getJiraVersions() {
        try {
            JiraObjects.Response response = this.getJiraResponse();

            if (response == null) {
                throw new IllegalStateException("The Jira response is null");
            } else if (response.versions == null) {
                throw new IllegalStateException("The Jira response contains no versions");
            }

            return new ArrayList<>(response.versions);
        } catch (ExecutionException | IOException | InterruptedException e) {
            LOGGER.error("There was an error getting the Jira versions.", e);
            return new ArrayList<>();
        }
    }

    private MinecraftObjects.Response getMinecraftResponse()
            throws ExecutionException, InterruptedException, IOException {
        SimpleHttpRequest request = SimpleHttpRequests.get(Config.MINECRAFT_URL);
        final SimpleHttpResponse response = this.httpClient.execute(request,
                new FutureCallback<>() {
                    @Override
                    public void completed(SimpleHttpResponse result) {
                        LOGGER.debug("{} -> {}", Config.MINECRAFT_URL, result.getCode());
                    }

                    @Override
                    public void failed(Exception ex) {
                        LOGGER.error(Config.MINECRAFT_URL, ex);
                        throw new IllegalStateException(ex);
                    }

                    @Override
                    public void cancelled() {
                        LOGGER.warn("Request to {} has been canceled.", Config.MINECRAFT_URL);
                    }
                }).get();

        if (response.getCode() / 100 != 2) {
            LOGGER.error("Non 2xx status code {} sent by {}\n{}", response.getCode(),
                    Config.MINECRAFT_URL, response.getBodyText());
            return this.lastSuccessfulMcResponse;
        }

        MinecraftObjects.Response mcResponse = MAPPER.readValue(response.getBodyText(),
                MinecraftObjects.Response.class);
        this.lastSuccessfulMcResponse = mcResponse;
        return mcResponse;
    }

    private JiraObjects.Response getJiraResponse()
            throws ExecutionException, InterruptedException, IOException {
        SimpleHttpRequest request = SimpleHttpRequests.get(Config.JIRA_URL);
        final SimpleHttpResponse response = this.httpClient.execute(request,
                new FutureCallback<>() {
                    @Override
                    public void completed(SimpleHttpResponse result) {
                        LOGGER.debug("{} -> {}", Config.JIRA_URL, result.getCode());
                    }

                    @Override
                    public void failed(Exception ex) {
                        LOGGER.error(Config.JIRA_URL, ex);
                        throw new IllegalStateException(ex);
                    }

                    @Override
                    public void cancelled() {
                        LOGGER.warn("Request to {} has been canceled.", Config.JIRA_URL);
                    }
                }).get();

        if (response.getCode() / 100 != 2) {
            LOGGER.error("Non 2xx status code {} sent by {}\n{}", response.getCode(),
                    Config.JIRA_URL, response.getBodyText());
            return this.lastSuccessfulJiraResponse;
        }

        JiraObjects.Response jiraResponse = MAPPER.readValue(response.getBodyText(),
                JiraObjects.Response.class);
        this.lastSuccessfulJiraResponse = jiraResponse;
        return jiraResponse;
    }

    /**
     * Find a different element in two arraylists.
     *
     * @param a the base list
     * @param b the list where to find the mismatch
     * @param <T> type of the arraylists
     * @return an optional of the mismatched element
     */
    private <T> Optional<T> findMismatch(ArrayList<T> a, ArrayList<T> b) {
        int mismatchedIndex = Arrays.mismatch(a.toArray(), b.toArray());
        return mismatchedIndex == -1 ? Optional.empty() : Optional.of(b.get(mismatchedIndex));
    }
}
