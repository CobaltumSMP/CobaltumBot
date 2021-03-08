# CobaltumBot
This bot is built for a specific server in mind. If you want to use if, you can disable the modules you don't need

## Configuration
The bot is configured via environment variables

### Main configuration
| Variable               | Required | Description                      | Comment     |
| :--------------------: | :------: | -------------------------------- | ----------- |
| `DISCORD_TOKEN`        | `true`   | The discord bot token            | You must use it either as a command arg or as an env variable |
| `PREFIX`               | `true`   | The prefix for the bot commands  |
| `GUILD_ID_MAIN`        | `false`  | The ID of the main guild for the bot. Required by some commands and most of the other `ID` variables |
| `CHANNEL_ID_BROADCAST` | `false`  | The ID of the channel to send the messages when using the `broadcast` command |
| `ROLE_ID_DEV`          | `false`  | The ID of the Developer role     |
| `ROLE_ID_STAFF`        | `false`  | The ID of the Staff role         |
| `ROLE_ID_MOD`          | `false`  | The ID of the Moderator role     |
| `ROLE_ID_ADMIN`        | `false`  | The ID of the Administrator role |
| `ROLE_ID_OWNER`        | `false`  | The ID of the Owner role         |

### Version check module configuration
The variables are only required if you don't disable the module

| Variable                         | Required | Description                                                         |
| :------------------------------: | :------: | ------------------------------------------------------------------- |
| `MODULE_VERSION_CHECKER_ENABLED` | `false`  | If the Version checker module should be enabled                     |
| `VC_MINECRAFT_URL`               | `true`   | The URL the version checker should check for Minecraft updates      |
| `VC_JIRA_URL`                    | `true`   | The URL the version checker should check for Jira updates           |
| `CHANNEL_ID_VC_MC`               | `true`   | The ID of the channel to send notifications about Minecraft updates |
| `CHANNEL_ID_VC_JIRA`             | `true`   | The ID of the channel to send notifications about Jira updates      |

### RCON Module configuration
The variables are only required if you don't disable the module

| Variable              | Required | Description                                                  |
| :-------------------: | :------: | ------------------------------------------------------------ |
| `MODULE_RCON_ENABLED` | `false`  | If the RCON module should be enabled                         |
| `CHANNEL_ID_RCON`     | `true`   | The ID of the channel the module should use as command input |
| `RCON_HOST`           | `true`   | The IP of the Minecraft server for the RCON connection       |
| `RCON_PORT`           | `true`   | The port of the Minecraft server for the RCON connection     |
| `RCON_PASS`           | `true`   | The password of the Minecraft server RCON connection         |

### Ticket system module
| Variable                       | Required | Description                                   |
| :----------------------------: | :------: | --------------------------------------------- |
| `MODULE_TICKET_SYSTEM_ENABLED` | `false`  | If the Ticket system module should be enabled |