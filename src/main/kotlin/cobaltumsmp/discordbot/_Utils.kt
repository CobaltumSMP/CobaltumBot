package cobaltumsmp.discordbot

import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.OverwriteType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Guild
import dev.kord.rest.builder.channel.PermissionOverwriteBuilder
import kotlinx.datetime.DateTimePeriod

internal fun escapeCodeBlocks(text: String): String =
    text.replace("```", "\\`\\`\\`")

internal fun multipleSnowflakes(key: String): List<Snowflake> =
    envOrNull(key)?.let { v -> v.split(",").map { Snowflake(it.trim()) } } ?: emptyList()

internal suspend fun mainGuild(kord: Kord): Guild? = kord.getGuild(GUILD_MAIN)

internal fun memberOverwrite(memberId: Snowflake, builder: PermissionOverwriteBuilder.() -> Unit) =
    PermissionOverwriteBuilder(OverwriteType.Member, memberId).apply(builder).toOverwrite()

internal fun roleOverwrite(roleId: Snowflake, builder: PermissionOverwriteBuilder.() -> Unit) =
    PermissionOverwriteBuilder(OverwriteType.Role, roleId).apply(builder).toOverwrite()

internal fun DateTimePeriod.toReadableString(): String {
    val builder = StringBuilder()

    if (seconds > 0) builder.append("$seconds seconds ")
    if (minutes > 0) builder.append("$minutes minutes ")
    if (hours > 0) builder.append("$hours hours ")
    if (days > 0) builder.append("$days days ")
    if (months > 0) builder.append("$months months ")
    if (years > 0) builder.append("$years years")

    return builder.toString().trim()
}

internal fun permissionToString(permission: Permission): String = when (permission) {
    Permission.CreateInstantInvite -> "Create Instant Invite"
    Permission.KickMembers -> "Kick Members"
    Permission.BanMembers -> "Ban Members"
    Permission.Administrator -> "Administrator"
    Permission.ManageChannels -> "Manage Channels"
    Permission.ManageGuild -> "Manage Guild"
    Permission.AddReactions -> "Add Reactions"
    Permission.ViewAuditLog -> "View Audit Log"
    Permission.Stream -> "Stream"
    Permission.ViewChannel -> "View Channel"
    Permission.SendMessages -> "Send Messages"
    Permission.SendTTSMessages -> "Send TTS Messages"
    Permission.ManageMessages -> "Manage Messages"
    Permission.EmbedLinks -> "Embed Links"
    Permission.AttachFiles -> "Attach Files"
    Permission.ReadMessageHistory -> "Read Message History"
    Permission.MentionEveryone -> "Mention Everyone"
    Permission.UseExternalEmojis -> "Use External Emojis"
    Permission.ViewGuildInsights -> "View Guild Insights"
    Permission.Connect -> "Connect"
    Permission.Speak -> "Speak"
    Permission.MuteMembers -> "Mute Members"
    Permission.DeafenMembers -> "Deafen Members"
    Permission.MoveMembers -> "Move Members"
    Permission.UseVAD -> "Use Voice Activity"
    Permission.PrioritySpeaker -> "Priority Speaker"
    Permission.ChangeNickname -> "Change Nickname"
    Permission.ManageNicknames -> "Manage Nicknames"
    Permission.ManageRoles -> "Manage Roles"
    Permission.ManageWebhooks -> "Manage Webhooks"
    Permission.ManageEmojis -> "Manage Emojis"
    Permission.UseSlashCommands -> "Use Slash Commands"
    Permission.RequestToSpeak -> "Request to Speak"
    Permission.ManageThreads -> "Manage Threads"
    Permission.CreatePublicThreads -> "Create Public Threads"
    Permission.CreatePrivateThreads -> "Create Private Threads"
    Permission.SendMessagesInThreads -> "Send Messages in Threads"
    Permission.All -> "All"
}
