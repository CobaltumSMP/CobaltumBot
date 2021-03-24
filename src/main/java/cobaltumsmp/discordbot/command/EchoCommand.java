package cobaltumsmp.discordbot.command;

import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageBuilder;

import java.util.List;

/**
 * Send a message to this channel or the provided one ({@code channel}).
 *
 * <p>Usage: {@code echo [<channel>] <message>}</p>
 *
 * @see BroadcastCommand
 */
public class EchoCommand implements Command {
    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String[] description() {
        return new String[]{I18nUtil.key("command.echo.description")};
    }

    @Override
    public int useArgs() {
        return 1;
    }

    @Override
    public boolean countAttachmentsAsArgs() {
        return true;
    }

    @Override
    public void execute(Message message, List<String> args) {
        TextChannel channel;
        if (message.getChannel().getType().isServerChannelType()
                && message.getMentionedChannels().size() >= 1
                && args.get(0).matches("<#[\\d]+>")) {
            channel = message.getMentionedChannels().get(0);

            if (Util.isMessageUserAuthorEmpty(message)) {
                Util.unexpectedErrorMessageResponse(message);
                return;
            }

            if (!channel.canWrite(message.getUserAuthor().get())) {
                message.getChannel().sendMessage(I18nUtil.key("command.echo.no_permission"));
                return;
            }

            args.remove(0);
        } else {
            channel = message.getChannel();
        }

        String content = String.join(" ", args);
        if (message.getAttachments().size() > 0) {
            MessageBuilder msgBuilder = new MessageBuilder().append(content);
            message.getAttachments().forEach(messageAttachment ->
                    msgBuilder.addAttachment(messageAttachment.getUrl()));
            msgBuilder.send(channel);
        } else {
            channel.sendMessage(content);
        }
    }
}
