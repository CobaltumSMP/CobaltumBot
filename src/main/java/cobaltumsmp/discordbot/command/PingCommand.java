package cobaltumsmp.discordbot.command;

import com.vdurmont.emoji.EmojiParser;
import org.javacord.api.entity.message.Message;

import java.util.Arrays;
import java.util.List;

public class PingCommand implements Command {
    @Override
    public String name() {
        return "ping";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("pong", "ping_pong", EmojiParser.parseToUnicode(":ping_pong:"));
    }

    @Override
    public String[] description() {
        return new String[]{"Pong! Get the bot ping."};
    }

    @Override
    public void execute(Message message, List<String> args) {
        message.getChannel().sendMessage(EmojiParser.parseToUnicode(":ping_pong:") + " Pong! "
                + message.getApi().getLatestGatewayLatency().getNano() / 1000000 + "ms");
    }
}
