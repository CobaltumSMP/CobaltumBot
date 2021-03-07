package cobaltumsmp.discordbot.command;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.message.Message;

import java.util.Arrays;
import java.util.List;

public class SetPresenceCommand implements Command {
    @Override
    public String name() {
        return "setpresence";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("presence", "presenceset");
    }

    @Override
    public String[] description() {
        return new String[]{"Set the bot presence."};
    }

    @Override
    public boolean mainGuild() {
        return true;
    }

    @Override
    public int useArgs() {
        return 2;
    }

    @Override
    public Roles roleRequired() {
        return Roles.DEV;
    }

    @Override
    public void execute(Message message, List<String> args) {
        String type = args.get(0).toUpperCase();
        if (!type.equals("LISTENING") && !type.equals("PLAYING") && !type.equals("WATCHING")) {
            message.getChannel().sendMessage(args.get(0) + " is not a valid presence type. Presence types: \n`LISTENING`, `PLAYING`, `WATHCING`");
            return;
        }

        String text = String.join(" ", args.subList(1, args.size()));
        Main.getApi().updateActivity(ActivityType.valueOf(type), text);

        message.getChannel().sendMessage(String.format("Presence set to '%s **%s**'", type.toLowerCase(), text));
    }
}
