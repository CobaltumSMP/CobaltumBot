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

public class BroadcastCommand implements Command {
    @Override
    public String name() {
        return "broadcast";
    }

    @Override
    public String[] description() {
        return new String[]{"Like echo, but sends the message to the configured broadcast channel."};
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
        Optional<Channel> optionalChannel = Main.getApi().getChannelById(BotConfig.CHANNEL_ID_BROADCAST);
        optionalChannel.ifPresent(channel -> {
            TextChannel textChannel = channel.asTextChannel().get();
            String content = String.join(" ", args);
            if (message.getAttachments().size() > 0) {
                MessageBuilder msgBuilder = new MessageBuilder().append(content);
                message.getAttachments().forEach(messageAttachment -> msgBuilder.addAttachment(messageAttachment.getUrl()));
                msgBuilder.send(textChannel);
            } else {
                textChannel.sendMessage(content);
            }
        });
    }
}
