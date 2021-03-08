package cobaltumsmp.discordbot.command;

import cobaltumsmp.discordbot.BotConfig;
import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
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
        return new String[]{
            "Like echo, but sends the message to the configured broadcast channel."
        };
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
        Optional<Channel> channelOptional = Main.getApi()
                .getChannelById(BotConfig.CHANNEL_ID_BROADCAST);

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
                        "The configured channel for broadcasts is not of type 'text'.");
                Main.LOGGER.warn("The configured channel for broadcasts is not of type 'text'.");
            }
        } else {
            message.getChannel().sendMessage(
                    "The configured channel for broadcasts could not be found.");
            Main.LOGGER.warn("The configured channel for broadcasts could not be found.");
        }
    }
}
