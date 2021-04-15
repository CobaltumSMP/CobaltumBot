package cobaltumsmp.discordbot.command;

import cobaltumsmp.discordbot.BotConfig;
import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Send a message to the configured channel
 * ({@link cobaltumsmp.discordbot.BotConfig#CHANNEL_ID_BROADCAST}).
 *
 * <p>Usage: {@code broadcast <message>}</p>
 *
 * @see EchoCommand
 */
public class BroadcastCommand implements Command {
    @Override
    public String name() {
        return "broadcast";
    }

    @Override
    public String[] description() {
        return new String[]{I18nUtil.key("command.broadcast.description")};
    }

    @Override
    public boolean useOnGuild() {
        return true;
    }

    @Override
    public int useArgs() {
        return 1;
    }

    @Override
    public Roles roleRequired() {
        return Roles.MOD;
    }

    @Override
    public boolean countAttachmentsAsArgs() {
        return true;
    }

    @Override
    public void execute(Message message, List<String> args) {
        List<Long> channelIds = BotConfig.CHANNEL_ID_BROADCAST;
        if (channelIds.isEmpty()) {
            message.getChannel().sendMessage(I18nUtil.key("command.broadcast.no_channel"));
            Main.LOGGER.warn("There isn't any configured broadcasts channel.");
        } else if (channelIds.size() == 1) {
            Optional<Channel> channelOptional = Main.getApi().getChannelById(
                    channelIds.get(0));

            if (channelOptional.isPresent()) {
                Optional<TextChannel> textChannelOptional = channelOptional.get().asTextChannel();
                if (textChannelOptional.isPresent()) {
                    TextChannel textChannel = textChannelOptional.get();
                    String content = String.join(" ", args);

                    if (message.getAttachments().size() > 0) {
                        MessageBuilder msgBuilder = new MessageBuilder().append(content);
                        message.getAttachments().forEach(messageAttachment ->
                                msgBuilder.addAttachment(messageAttachment.getUrl()));
                        msgBuilder.send(textChannel);
                    } else {
                        textChannel.sendMessage(content);
                    }
                } else {
                    message.getChannel().sendMessage(
                            I18nUtil.key("command.broadcast.channel_invalid_type"));
                    Main.LOGGER.warn(
                            "The configured channel for broadcasts is not of type 'text'.");
                }
            } else {
                message.getChannel().sendMessage(
                        I18nUtil.key("command.broadcast.channel_not_found"));
                Main.LOGGER.warn("The configured channel for broadcasts could not be found.");
            }
        } else {
            for (Long channelId : channelIds) {
                Optional<Channel> channelOptional = Main.getApi().getChannelById(
                        channelId);

                if (channelOptional.isPresent()) {
                    Optional<TextChannel> textChannelOptional = channelOptional.get()
                            .asTextChannel();
                    if (textChannelOptional.isPresent()) {
                        TextChannel textChannel = textChannelOptional.get();
                        String content = String.join(" ", args);

                        if (message.getAttachments().size() > 0) {
                            MessageBuilder msgBuilder = new MessageBuilder().append(content);
                            message.getAttachments().forEach(messageAttachment ->
                                    msgBuilder.addAttachment(messageAttachment.getUrl()));
                            msgBuilder.send(textChannel);
                        } else {
                            textChannel.sendMessage(content);
                        }
                    } else {
                        message.getChannel().sendMessage(
                                I18nUtil.formatKey("command.broadcast.a_channel_invalid_type",
                                        channelId));
                        Main.LOGGER.warn(
                                "One of the configured channels for broadcasts is not of type "
                                        + "'text' ({}).", channelId);
                    }
                } else {
                    message.getChannel().sendMessage(
                            I18nUtil.formatKey("command.broadcast.a_channel_not_found", channelId));
                    Main.LOGGER.warn("One of the configured channels for broadcasts could not be "
                            + "found ({}).", channelId);
                }
            }
        }
    }
}
