package cobaltumsmp.discordbot.command;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.message.Message;

import java.util.Arrays;
import java.util.List;

/**
 * Set the bot activity.
 */
public class SetPresenceCommand implements Command {
    @Override
    public String name() {
        return "setpresence";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("presence", "presenceset", "setactivity", "activity", "activityset");
    }

    @Override
    public String[] description() {
        return new String[]{I18nUtil.key("command.setpresence.description")};
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
            message.getChannel().sendMessage(I18nUtil.formatKey("command.setpresence.invalid_type",
                    args.get(0), "`LISTENING`, `PLAYING`, `WATHCING`"));
            return;
        }

        String text = String.join(" ", args.subList(1, args.size()));
        Main.getApi().updateActivity(ActivityType.valueOf(type), text);

        message.getChannel().sendMessage(I18nUtil.formatKey("command.setpresence.set",
                type.toLowerCase(), text));
    }
}
