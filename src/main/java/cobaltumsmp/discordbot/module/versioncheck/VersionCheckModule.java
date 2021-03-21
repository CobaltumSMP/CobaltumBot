package cobaltumsmp.discordbot.module.versioncheck;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.module.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A module that constantly checks the configured URLs ({@link Config#JIRA_URL}
 * and {@link Config#MINECRAFT_URL}) for new versions.
 */
public class VersionCheckModule extends Module {
    protected static final Logger LOGGER = LogManager.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ScheduledExecutorService THREAD_POOL = Executors.newScheduledThreadPool(3);
    private final CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault();
    private TextChannel mcUpdatesChannel = null;
    private TextChannel jiraUpdatesChannel = null;
    private List<MinecraftObjects.Version> minecraftVersions;
    private List<JiraObjects.Version> jiraVersions;
    private boolean checking = false;

    @Override
    public String name() {
        return "Version checker";
    }

    @Override
    public void init() {
        Optional<Channel> chn;
        Channel chn1;

        // Get the MC updates channel
        if (Util.isChannelByIdEmpty((chn
                = Main.getApi().getChannelById(Config.CHANNEL_ID_MC_UPDATES)))
                || Util.isTextChannelEmpty((chn1 = chn.get()))) {
            LOGGER.warn("The Minecraft updates channel ('{}') is invalid.",
                    Config.CHANNEL_ID_MC_UPDATES);
        } else {
            this.mcUpdatesChannel = chn1.asTextChannel().get();
        }

        // Get the Jira updates channel
        if (Util.isChannelByIdEmpty((chn
                = Main.getApi().getChannelById(Config.CHANNEL_ID_JIRA_UPDATES)))
                || Util.isTextChannelEmpty((chn1 = chn.get()))) {
            LOGGER.warn("The Jira updates channel ('{}') is invalid.",
                    Config.CHANNEL_ID_JIRA_UPDATES);
        } else {
            this.jiraUpdatesChannel = chn1.asTextChannel().get();
        }

        this.checking = true;

        LOGGER.info("Fetching initial data.");
        httpClient.start();

        this.minecraftVersions = this.getMinecraftVersions();
        this.jiraVersions = this.getJiraVersions();

        this.checking = false;

        LOGGER.debug("Scheduling version check task.");

        int delay = 30;

        THREAD_POOL.scheduleAtFixedRate(() -> {
            LOGGER.debug("Running scheduled version check task.");

            try {
                this.checkUpdates();
            } catch (Exception e) {
                LOGGER.error(
                        "Encountered an error while running the scheduled version check task.", e);
            }
        }, delay / 2, delay, TimeUnit.SECONDS);

        LOGGER.debug("Scheduled version check task with {} seconds of delay.", delay);

        this.loaded = true;
        LOGGER.info("Module ready.");
    }

    protected void checkUpdates() {
        if (this.checking) {
            LOGGER.warn("A version check is already running! Skipping version check.");
            return;
        }

        this.checking = true;

        MinecraftObjects.Version mcVersion = this.checkMinecraftUpdates();
        if (mcVersion != null && this.mcUpdatesChannel != null) {
            this.mcUpdatesChannel.sendMessage(String.format(
                    "A new Minecraft %s is out: %s!", mcVersion.type, mcVersion.id));
        }

        JiraObjects.Version jiraVersion = this.checkJiraUpdates();
        if (jiraVersion != null && this.jiraUpdatesChannel != null) {
            this.jiraUpdatesChannel.sendMessage(String.format(
                    "A new version has been added to the Minecraft issue tracker: %s",
                    jiraVersion.name));
        }

        this.checking = false;
    }

    private MinecraftObjects.Version checkMinecraftUpdates() {
        LOGGER.debug("Checking for Minecraft updates.");

        List<MinecraftObjects.Version> versions = this.getMinecraftVersions();
        Optional<MinecraftObjects.Version> newVersion = versions.stream().filter(v ->
                !this.minecraftVersions.contains(v)).findFirst();

        LOGGER.info("Minecraft | New version: {}",
                newVersion.isEmpty() ? "N/A" : newVersion.get().id);
        LOGGER.info("Minecraft | Total versions: {}", versions.size());

        this.minecraftVersions = versions;

        return newVersion.orElse(null);
    }

    private JiraObjects.Version checkJiraUpdates() {
        LOGGER.debug("Checking for Jira updates.");

        List<JiraObjects.Version> versions = this.getJiraVersions();
        Optional<JiraObjects.Version> newVersion = versions.stream().filter(v ->
                !this.jiraVersions.contains(v)).findFirst();

        LOGGER.info("Jira | New version: {}",
                newVersion.isEmpty() ? "N/A" : newVersion.get().name);
        LOGGER.info("Jira | Total versions: {}", versions.size());

        this.jiraVersions = versions;

        return newVersion.orElse(null);
    }

    private List<MinecraftObjects.Version> getMinecraftVersions() {
        try {
            MinecraftObjects.Response response = this.getMinecraftResponse();

            return response.versions;
        } catch (ExecutionException | InterruptedException | IOException e) {
            LOGGER.error("There was an error getting the Minecraft versions.", e);
            return new ArrayList<>();
        }
    }

    private List<JiraObjects.Version> getJiraVersions() {
        try {
            JiraObjects.Response response = this.getJiraResponse();

            return response.versions;
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

        return MAPPER.readValue(response.getBodyText(), MinecraftObjects.Response.class);
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

        return MAPPER.readValue(response.getBodyText(), JiraObjects.Response.class);
    }
}
