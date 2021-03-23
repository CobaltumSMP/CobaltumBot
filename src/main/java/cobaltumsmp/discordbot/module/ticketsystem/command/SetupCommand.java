package cobaltumsmp.discordbot.module.ticketsystem.command;

import cobaltumsmp.discordbot.Main;
import cobaltumsmp.discordbot.Roles;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import cobaltumsmp.discordbot.module.Module;
import cobaltumsmp.discordbot.module.ticketsystem.TicketConfig;
import cobaltumsmp.discordbot.module.ticketsystem.TicketSystemModule;
import com.vdurmont.emoji.EmojiParser;
import org.apache.cayenne.ObjectContext;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.entity.channel.ServerChannelUpdater;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Permissions;
import org.javacord.api.entity.permission.PermissionsBuilder;
import org.javacord.api.entity.permission.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Setup a new {@link TicketConfig}.
 */
public class SetupCommand extends Module.ModuleCommand<TicketSystemModule> {
    public SetupCommand(TicketSystemModule module) {
        super(module);
    }

    @Override
    public String name() {
        return "setup";
    }

    @Override
    public List<String> aliases() {
        return Arrays.asList("ticketsetup", "setupticket");
    }

    @Override
    public String[] description() {
        return new String[]{I18nUtil.key("ticket_system.command.setup.description")};
    }

    @Override
    public boolean mainGuild() {
        return true;
    }

    @Override
    public int useArgs() {
        return 4;
    }

    @Override
    public Roles roleRequired() {
        return Roles.ADMIN;
    }

    @Override
    public void execute(Message message, List<String> args) {
        if (this.cantExecute(message.getChannel())) {
            return;
        }

        DiscordApi api = Main.getApi();

        // Get the channel where the message with the given id should be
        List<ServerTextChannel> mentionedChannels = new ArrayList<>(message.getMentionedChannels());
        // Wrap in ArrayList to allow modifications ^
        Optional<Channel> channelOptional;
        Optional<TextChannel> textChannelOptional;
        Optional<ServerTextChannel> serverTextChannelOptional;
        ServerTextChannel messageChannel;

        if (mentionedChannels.size() > 0) {
            messageChannel = mentionedChannels.remove(0);
        } else {
            if ((channelOptional = api.getChannelById(args.get(0))).isEmpty()) {
                message.getChannel().sendMessage(I18nUtil.formatKey("error.channel.id_not_found",
                        args.get(0)));
                return;
            } else if ((textChannelOptional = channelOptional.get().asTextChannel()).isEmpty()) {
                message.getChannel().sendMessage(I18nUtil.formatKey("error.channel.invalid_type",
                        args.get(0)));
                return;
            } else if ((serverTextChannelOptional = textChannelOptional.get().asServerTextChannel())
                    .isEmpty()) {
                message.getChannel().sendMessage(I18nUtil.formatKey("error.channel.not_server_text",
                        args.get(0)));
                return;
            } else {
                messageChannel = serverTextChannelOptional.get();
            }

            if (!messageChannel.canYouWrite()) {
                message.getChannel().sendMessage(I18nUtil.key("error.channel.cant_write"));
                return;
            } else if (!messageChannel.canYouAddNewReactions()) {
                message.getChannel().sendMessage(I18nUtil.key("error.channel.cant_react"));
                return;
            }
        }

        Optional<ChannelCategory> channelCategoryOptional;
        ChannelCategory ticketCategory;
        ChannelCategory closedTicketCategory;

        if ((channelCategoryOptional = api.getChannelCategoryById(args.get(1))).isEmpty()) {
            message.getChannel().sendMessage(I18nUtil.formatKey("error.category.id_not_found",
                    args.get(1)));
            return;
        } else {
            ticketCategory = channelCategoryOptional.get();
        }
        if ((channelCategoryOptional = api.getChannelCategoryById(args.get(2))).isEmpty()) {
            message.getChannel().sendMessage(I18nUtil.formatKey("error.category.id_not_found",
                    args.get(2)));
            return;
        } else {
            closedTicketCategory = channelCategoryOptional.get();
        }

        List<Role> roles;
        if (message.getMentionedRoles().size() > 0) {
            roles = new ArrayList<>(message.getMentionedRoles());
        } else {
            roles = new ArrayList<>();
            Optional<Role> roleOptional;
            for (int i = 3; i < args.size(); ++i) {
                if ((roleOptional = api.getRoleById(args.get(i))).isEmpty()) {
                    message.getChannel().sendMessage(I18nUtil.formatKey("error.role.id_not_found",
                            args.get(i)));
                    return;
                } else {
                    roles.add(roleOptional.get());
                }
            }
        }

        // The message to listen for reactions
        Message mainMsg;
        try {
            mainMsg = messageChannel.sendMessage(new EmbedBuilder().setDescription(
                    I18nUtil.formatKey("ticket_system.open_embed_content",
                            EmojiParser.parseToUnicode(":ticket:")))).join();
        } catch (Exception e) {
            TicketSystemModule.LOGGER.error(e);
            message.getChannel().sendMessage(I18nUtil.key("error.message.could_not_send"));
            return;
        }

        List<String> strRoles = roles.stream().mapToLong(Role::getId).mapToObj(Long::toString)
                .collect(Collectors.toList());

        ObjectContext context = this.module.getContext();

        TicketConfig config = context.newObject(TicketConfig.class);
        config.setMessageId(mainMsg.getId());
        config.setMessageChannelId(messageChannel.getId());
        config.setTicketCategoryId(ticketCategory.getId());
        config.setClosedTicketCategoryId(closedTicketCategory.getId());
        config.setRoles(String.join(",", strRoles));

        context.commitChanges();
        this.module.updateConfigs();

        this.setupPermissions(message.getChannel(), ticketCategory, closedTicketCategory, roles);
        mainMsg.addReaction(EmojiParser.parseToUnicode(":ticket:"));
    }

    private void setupPermissions(TextChannel commandChannel, ChannelCategory ticketCategory,
                                  ChannelCategory closedTicketCategory,
                                  List<Role> globalAccessRoles) {
        Permissions ticketCategorySelfPermissions = new PermissionsBuilder().setAllowed(
                PermissionType.READ_MESSAGES, PermissionType.SEND_MESSAGES,
                PermissionType.MANAGE_MESSAGES, PermissionType.MANAGE_CHANNELS,
                PermissionType.ADD_REACTIONS, PermissionType.MANAGE_ROLES).build();
        Permissions ticketCategoryRolePermissions = new PermissionsBuilder().setAllowed(
                PermissionType.READ_MESSAGES, PermissionType.SEND_MESSAGES).build();
        Permissions closedTicketCategorySelfPermissions = new PermissionsBuilder().setAllowed(
                PermissionType.MANAGE_CHANNELS, PermissionType.MANAGE_ROLES).build();

        ServerChannelUpdater ticketCategoryUpdater = ticketCategory.createUpdater();
        ServerChannelUpdater closedTicketCategoryUpdater = closedTicketCategory.createUpdater();

        ticketCategoryUpdater.addPermissionOverwrite(Main.getApi().getYourself(),
                ticketCategorySelfPermissions);
        closedTicketCategoryUpdater.addPermissionOverwrite(Main.getApi().getYourself(),
                closedTicketCategorySelfPermissions);

        for (Role role : globalAccessRoles) {
            ticketCategoryUpdater.addPermissionOverwrite(role, ticketCategoryRolePermissions);
        }

        try {
            ticketCategoryUpdater.update().join();
        } catch (Throwable t) {
            TicketSystemModule.LOGGER.warn(t);
            new MessageBuilder().setContent(
                    I18nUtil.key("ticket_system.error.setup.ticket_category_perms")).appendCode("",
                    "CobaltumBot: View channels, Send Messages, Manage Messages,"
                            + " Manage Channels, Add Reactions, Manage Permissions\n"
                    + "Provided roles: View channels, Send Messages").send(commandChannel);
        }

        try {
            closedTicketCategoryUpdater.update().join();
        } catch (Throwable t) {
            TicketSystemModule.LOGGER.warn(t);
            new MessageBuilder().setContent(
                    I18nUtil.key("ticket_system.error.setup.closed_ticket_category_perms"))
                    .appendCode("",
                            "CobaltumBot: Manage Messages, Manage Permissions")
                    .send(commandChannel);
        }
    }
}
