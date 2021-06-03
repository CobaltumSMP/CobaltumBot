package cobaltumsmp.discordbot.module.versioncheck;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import cobaltumsmp.discordbot.module.Module;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
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
import org.javacord.api.entity.channel.TextChannel;

import javax.annotation.Nullable;
import javax.swing.text.html.Option;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A module that constantly checks the configured URLs ({@link Config#JIRA_URL}
 * and {@link Config#MINECRAFT_URL}) for new versions.
 */
public class VersionCheckModule extends Module {
    protected static final Logger LOGGER = LogManager.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ScheduledExecutorService THREAD_POOL = Executors.newScheduledThreadPool(4,
            new ThreadFactoryBuilder().setNameFormat("version-checker-thread-%d").build());
    private final CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault();
    private final Collection<TextChannel> mcUpdatesChannels = Sets.newHashSet();
    private final Collection<TextChannel> jiraUpdatesChannels = Sets.newHashSet();
    private final List<MinecraftObjects.Version> mcVersions = new ArrayList<>();
    private final List<JiraObjects.Version> jiraVersions = new ArrayList<>();
    private ScheduledFuture<?> scheduledChecks;
    private boolean checking = false;

    @Override
    public String name() {
        return "Version checker";
    }

    @Override
    public void init() {
        Optional<TextChannel> textChannelOptional;

        // Get the MC updates channel(s)
        for (Long id : Config.CHANNEL_ID_MC_UPDATES) {
            if ((textChannelOptional = Main.getApi().getTextChannelById(id)).isEmpty()) {
                LOGGER.warn("One of the Minecraft updates channels ('{}') is invalid.", id);
            } else {
                this.mcUpdatesChannels.add(textChannelOptional.get());
            }
        }

        // Get the Jira updates channel(s)
        for (Long id : Config.CHANNEL_ID_JIRA_UPDATES) {
            if ((textChannelOptional = Main.getApi().getTextChannelById(id)).isEmpty()) {
                LOGGER.warn("One of the Jira updates channels ('{}') is invalid.", id);
            } else {
                this.jiraUpdatesChannels.add(textChannelOptional.get());
            }
        }

        if (Config.MINECRAFT_URL.isEmpty()) {
            LOGGER.error("Minecraft url is unset!");
            this.setEnabled(false);
            return;
        } else if (Config.JIRA_URL.isEmpty()) {
            LOGGER.error("Jira url is unset!");
            this.setEnabled(false);
            return;
        }

        this.httpClient.start();
        this.fetchInitialData(0);
    }

    private void fetchInitialData(int attempt) {
        LOGGER.info("Fetching initial data.");
        List<MinecraftObjects.Version> mcVersions = new ArrayList<>();
        List<JiraObjects.Version> jiraVersions = new ArrayList<>();

        try {
            mcVersions.addAll(this.fetchMinecraftVersions());
        } catch (ExecutionException e) {
            LOGGER.error(
                    "Failed to fetch the initial Minecraft versions. Is the server down?", e);
        } catch (InterruptedException e) {
            LOGGER.error("Failed to fetch the initial Minecraft versions "
                    + "because the thread was interrupted", e);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse the received response when fetching "
                    + "the initial Minecraft versions", e);
        }

        try {
            jiraVersions.addAll(this.fetchJiraVersions());
        } catch (ExecutionException e) {
            LOGGER.error(
                    "Failed to fetch the initial Jira versions. Is the server down?", e);
        } catch (InterruptedException e) {
            LOGGER.error("Failed to fetch the initial Jira versions "
                    + "because the thread was interrupted", e);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse the received response when fetching "
                    + "the initial Jira versions", e);
        }

        this.mcVersions.addAll(mcVersions);
        this.jiraVersions.addAll(jiraVersions);
        if (this.mcVersions.isEmpty() && this.jiraVersions.isEmpty()) {
            if (attempt >= 5) {
                LOGGER.error("Too many failed attempts to fetch the initial data. "
                        + "Disabling module");
                this.setEnabled(false);
            } else {
                LOGGER.warn("Both Minecraft and Jira versions were empty, "
                        + "attempting again in a minute");
                final int attempt1 = ++attempt;
                THREAD_POOL.schedule(() -> this.fetchInitialData(attempt1), 1, TimeUnit.MINUTES);
            }
            return;
        } else if (this.mcVersions.isEmpty()) {
            LOGGER.warn("No initial data for Minecraft, it will be fetched next check");

            LOGGER.info("Jira      | Total versions: {}", this.jiraVersions.size());
            LOGGER.info("Jira      | Latest version: {}", this.getLatestJiraVersionName());
        } else if (this.jiraVersions.isEmpty()) {
            LOGGER.warn("No initial data for Jira, it will be fetched next check");

            LOGGER.info("Minecraft | Total versions: {}", this.mcVersions.size());
            LOGGER.info("Minecraft | Latest version: {}", this.getLatestMcVersionId());
        } else {
            LOGGER.info("Minecraft | Total versions: {}", this.mcVersions.size());
            LOGGER.info("Minecraft | Latest version: {}", this.getLatestMcVersionId());
            LOGGER.info("Jira      | Total versions: {}", this.jiraVersions.size());
            LOGGER.info("Jira      | Latest version: {}", this.getLatestJiraVersionName());
        }

        LOGGER.debug("Scheduled version check task with {} seconds of delay.", Config.CHECK_DELAY);
        this.scheduledChecks = THREAD_POOL.scheduleAtFixedRate(() -> {
            LOGGER.debug("Running scheduled version check task.");
            this.checkUpdates();
        }, Config.CHECK_DELAY / 2, Config.CHECK_DELAY, TimeUnit.SECONDS);
        this.loaded = true;
        LOGGER.info("Module ready.");
    }

    @Override
    public @Nullable Consumer<DiscordApi> getInstallFunction() {
        return discordApi -> Main.loadCommand(new VersionCheckCommand(this));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            if (this.scheduledChecks != null) {
                this.scheduledChecks.cancel(false);
            }
            Main.unloadCommand("versioncheck");
        }
    }

    protected static String getMcVersionId(Optional<MinecraftObjects.Version> version) {
        return version.isPresent() ? version.get().id : "N/A";
    }

    protected Optional<MinecraftObjects.Version> getLatestMcVersion() {
        return !this.mcVersions.isEmpty()
                ? Optional.ofNullable(this.mcVersions.get(this.mcVersions.size() - 1))
                : Optional.empty();
    }

    protected String getLatestMcVersionId() {
        return getMcVersionId(this.getLatestMcVersion());
    }

    protected static String getJiraVersionName(Optional<JiraObjects.Version> version) {
        return version.isPresent() ? version.get().name : "N/A";
    }

    protected Optional<JiraObjects.Version> getLatestJiraVersion() {
        return !this.jiraVersions.isEmpty()
                ? Optional.ofNullable(this.jiraVersions.get(this.jiraVersions.size() - 1))
                : Optional.empty();
    }

    protected String getLatestJiraVersionName() {
        return getJiraVersionName(this.getLatestJiraVersion());
    }

    protected void checkUpdates() {
        if (this.checking) {
            LOGGER.warn("A version check is already running! Skipping version check.");
            return;
        }

        this.checking = true;


        try {
            Optional<MinecraftObjects.Version> mcVersion = this.checkMinecraftUpdates();
            if (mcVersion.isPresent() && !this.mcUpdatesChannels.isEmpty()) {
                MinecraftObjects.Version version = mcVersion.get();
                String url = null;
                String snapshotUrlFormat = Config.SNAPSHOT_ARTICLE_URL_FORMAT;
                if (version.type.equals("snapshot") && !snapshotUrlFormat.isEmpty()) {
                    url = String.format(snapshotUrlFormat, version.id);
                }

                String msg = I18nUtil.formatKey("version_checker.new_mc_version",
                        version.type, version.id);
                for (TextChannel chn : this.mcUpdatesChannels) {
                    chn.sendMessage(msg + (url != null ? "\n" + url : ""));
                }
            }

            Optional<JiraObjects.Version> jiraVersion = this.checkJiraUpdates();
            if (jiraVersion.isPresent() && !this.jiraUpdatesChannels.isEmpty()) {
                String msg = I18nUtil.formatKey("version_checker.new_jira_version",
                        jiraVersion.get().name);
                for (TextChannel chn : this.jiraUpdatesChannels) {
                    chn.sendMessage(msg);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Found uncaught exception while checking updates", e);
        }

        this.checking = false;

        // TODO: Handle too many exceptions in a row.
    }

    private Optional<MinecraftObjects.Version> checkMinecraftUpdates() {
        LOGGER.debug("Checking for Minecraft updates.");
        Optional<MinecraftObjects.Version> result = Optional.empty();

        try {
            List<MinecraftObjects.Version> fetched = this.fetchMinecraftVersions();
            if (this.mcVersions.size() < fetched.size()) {
                List<MinecraftObjects.Version> list = new ArrayList<>(fetched);
                list.removeAll(this.mcVersions);
                result = list.stream().findAny();
            }
        } catch (ExecutionException e) {
            // TODO
        } catch (InterruptedException e) {
            // TODO
        } catch (JsonProcessingException e) {
            // TODO
        }

        return result;
    }

    private Optional<JiraObjects.Version> checkJiraUpdates() {
        Optional<JiraObjects.Version> result = Optional.empty();

        try {
            List<JiraObjects.Version> fetched = this.fetchJiraVersions();
            if (this.mcVersions.size() < fetched.size()) {
                List<JiraObjects.Version> list = new ArrayList<>(fetched);
                list.removeAll(this.jiraVersions);
                result = list.stream().findAny();
            }
        } catch (ExecutionException e) {
            // TODO
        } catch (InterruptedException e) {
            // TODO
        } catch (JsonProcessingException e) {
            // TODO
        }

        return result;
    }

    private List<MinecraftObjects.Version> fetchMinecraftVersions() throws ExecutionException,
            InterruptedException, JsonProcessingException {
        SimpleHttpResponse response = makeRequest(Config.MINECRAFT_URL);
        MinecraftObjects.Response mcResponse = MAPPER.readValue(response.getBodyText(),
                MinecraftObjects.Response.class);
        return mcResponse.versions;
    }

    private List<JiraObjects.Version> fetchJiraVersions() throws ExecutionException,
            InterruptedException, JsonProcessingException {
        SimpleHttpResponse response = makeRequest(Config.JIRA_URL);
        JiraObjects.Response jiraResponse = MAPPER.readValue(response.getBodyText(),
                JiraObjects.Response.class);
        return jiraResponse.versions;
    }

    private SimpleHttpResponse makeRequest(String requestUrl) throws ExecutionException,
            InterruptedException {
        SimpleHttpRequest request = SimpleHttpRequests.get(requestUrl);
        return this.httpClient.execute(request, new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                LOGGER.debug("Request to '{}' completed, response code: {}", requestUrl,
                        result.getCode());
            }

            @Override
            public void failed(Exception e) {
                LOGGER.error("Request to '{}' failed", requestUrl, e);
            }

            @Override
            public void cancelled() {
                LOGGER.warn("Request to '{}' cancelled", requestUrl);
            }
        }).get();
    }
}
