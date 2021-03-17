package cobaltumsmp.discordbot.command;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import cobaltumsmp.discordbot.Util;
import com.google.common.base.Strings;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Print a list of the commands with their description.
 */
public class HelpCommand implements Command {
    @Override
    public String name() {
        return "help";
    }

    @Override
    public String[] description() {
        return new String[]{"Get information about the bot commands."};
    }

    @Override
    public void execute(Message message, List<String> args) {
        // TODO: Get help for specific command
        // Get the length of the longest command to align the descriptions
        int length = Main.COMMANDS.keySet().stream().map(String::length).max(Integer::compareTo)
                .orElse(-1);

        List<String> helpList = new ArrayList<>();

        if (Util.isMessageUserAuthorEmpty(message)) {
            Util.unexpectedErrorMessageResponse(message);
            return;
        }

        // Show only the commands the author has access to
        User author = message.getUserAuthor().get();
        Main.COMMANDS.values().stream()
                .filter(command -> Roles.checkRoles(author, command.roleRequired()))
                .forEach(command -> {
                    String name = Strings.padEnd(command.name(), length, ' ');
                    helpList.add(String.format("%s - %s", name, command.description()[0]));
                });

        EmbedBuilder embed = new EmbedBuilder().setColor(Color.decode("#085a7a"))
                .setTitle("Available commands")
                .setAuthor(message.getApi().getYourself().getDiscriminatedName(), "",
                        message.getApi().getYourself().getAvatar())
                .setDescription(String.format("```\n%s\n```", String.join("\n", helpList)))
                .setFooter("CobaltumBot");
        message.getChannel().sendMessage(embed);
    }
}
