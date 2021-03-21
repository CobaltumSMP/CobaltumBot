package cobaltumsmp.discordbot.event;

import cobaltumsmp.discordbot.BotConfig;
import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import cobaltumsmp.discordbot.Util;
import cobaltumsmp.discordbot.command.Command;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The main {@link MessageCreateEvent} listener.
 */
public class MessageListener implements MessageCreateListener {
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        // No recursion, ignore webhooks
        if (event.getMessageAuthor().isBotUser() || event.getMessageAuthor().isWebhook()) {
            return;
        }

        if (Main.EXCLUSIVE_CHANNELS.contains(event.getChannel().getId())) {
            return;
        }

        executeCommand(event.getMessage());
    }

    private void executeCommand(Message message) {
        if (!message.getContent().startsWith(BotConfig.PREFIX)) {
            return;
        }

        Main.SERVICE.submit(() -> {
            String[] argsArray = message.getContent().substring(BotConfig.PREFIX.length())
                    .trim().split(" +");
            List<String> args = new ArrayList<>(Arrays.asList(argsArray));

            // Get the command from the first argument, also remove it from the args
            String commandStr = args.remove(0).toLowerCase();

            // Try to get the command by command names
            Command command = Main.COMMANDS.get(commandStr);

            if (command == null) {
                // Try to get the command by command aliases
                for (Command command1 : Main.COMMANDS.values()) {
                    if (command1.aliases().size() > 0 && command1.aliases().contains(commandStr)) {
                        command = command1;
                        break;
                    }
                }

                if (command == null) {
                    // If command can't be found return
                    message.getChannel().sendMessage(String.format("'%s' is not a valid command.",
                            commandStr));
                    return;
                }
            }

            Main.LOGGER.debug("{}: Executing command '{}'.",
                    message.getAuthor().getDiscriminatedName(), command.name());

            List<MessageAttachment> attachments = message.getAttachments();
            if (command.useArgs() > 0 && args.size() <= 0
                    && !(command.countAttachmentsAsArgs() && attachments.size() > 0)) {
                message.getChannel().sendMessage("You must provide arguments.");
                Main.LOGGER.debug("{}: Didn't provide args for '{}'.",
                        message.getAuthor().getDiscriminatedName(), command.name());
                return;
            } else if (command.useArgs() > 0 && args.size() < command.useArgs()
                    && !(command.countAttachmentsAsArgs()
                    && attachments.size() >= command.useArgs())) {
                message.getChannel().sendMessage(String.format(
                        "You must provide at least %d argument(s).", command.useArgs()));
                Main.LOGGER.debug("{}: Didn't provide enough arguments for '{}'.",
                        message.getAuthor().getDiscriminatedName(), command.name());
                return;
            }

            if ((command.useOnGuild() && message.getServer().isEmpty())
                    || (command.mainGuild()
                    && message.getServer().get().getId() != BotConfig.GUILD_ID_MAIN)) {
                message.getChannel().sendMessage("You can't do that here.");
                Main.LOGGER.debug("{}: Tried to execute '{}' in a wrong channel.",
                        message.getAuthor().getDiscriminatedName(), command.name());
                return;
            }

            if (Util.isMessageUserAuthorEmpty(message)) {
                Util.unexpectedErrorMessageResponse(message);
                return;
            }

            if (!Roles.checkRoles(message.getUserAuthor().get(), command.roleRequired())) {
                message.getChannel().sendMessage("You don't have the role needed to do this.");
                Main.LOGGER.debug("{}: Didn't have the needed role for '{}'.",
                        message.getAuthor().getDiscriminatedName(), command.name());
                return;
            }

            command.execute(message, args);
            Main.LOGGER.debug("{}: Executed command '{}'.",
                    message.getAuthor().getDiscriminatedName(), command.name());
        });
    }
}
