package DiscordTeamBot;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Application {

	public static void main(String[] args) {
		new Application();
	}

	public Application() {
		try {
			try {
				JDA builder = JDABuilder.createDefault(System.getProperty("BotToken"))
						.setChunkingFilter(ChunkingFilter.ALL) // enable member chunking for all guilds
						.setMemberCachePolicy(MemberCachePolicy.ALL) // ignored if chunking is enabled
						.enableIntents(GatewayIntent.GUILD_MEMBERS).addEventListeners(new CommandListener()).build();
				builder.awaitReady();
//				deleteSlashCommand(builder);
				registrateSlashCommands(builder);
			} catch (InterruptedException e) {
				System.out.println(e.getMessage());
			}
		} catch (LoginException e) {
			System.out.println(e.getMessage());
		}
	}

	public void registrateSlashCommands(JDA builder) {
		builder.upsertCommand("build-teams", "Build your teams random generated")
				.addOption(OptionType.INTEGER, "teams", "The number of teams", true)
				.addOption(OptionType.BOOLEAN, "limit", "Sets a user limit per voice channel", true)
				.setDefaultEnabled(false).complete();
		builder.upsertCommand("win", "Define the winner of a match")
				.addOption(OptionType.INTEGER, "matchnumber", "The number of the match", true)
				.addOption(OptionType.INTEGER, "teamnumber", "The number of the team who has won", true).complete();
		builder.upsertCommand("cancel", "Cancel a match")
				.addOption(OptionType.INTEGER, "matchnumber", "The number of the match", true).complete();
		builder.upsertCommand("stats", "Get the stats of each member")
				.addOption(OptionType.MENTIONABLE, "member", "Optional search for specifc user", false).complete();
		builder.upsertCommand("edit-team", "Switch two players between teams")
				.addOption(OptionType.INTEGER, "matchnumber", "The number of the match", true)
				.addOption(OptionType.MENTIONABLE, "player-one", "Player one to switch with", true)
				.addOption(OptionType.MENTIONABLE, "player-two", "Player two to switch with", true).complete();
	}

	public void deleteSlashCommand(JDA builder) {
		builder.updateCommands().complete();
	}
}
