package DiscordTeamBot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

public class CommandListener extends ListenerAdapter {

	private final double MEMBERS_PER_PAGE = Double.parseDouble(System.getProperty("MembersPerPage", "20"));
	private final String PATH = System.getProperty("SaveMatchesPath", "/matches/");

	private SlashCommandInteractionEvent slashEvent;
	private ButtonInteractionEvent buttonEvent;
	private ModalInteractionEvent modalEvent;
	private TextChannel eventTextChannel;
	private Member member;
	private Guild guild;
	private String textChannelId;
	private String guildStatsPath;

	// Method for slash commands
	// Triggers if an slash command occurs
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		this.slashEvent = event;
		this.member = slashEvent.getMember();
		this.guild = slashEvent.getGuild();
		this.textChannelId = slashEvent.getChannel().getId();
		this.guildStatsPath = PATH + guild.getId() + ".txt";

		if (slashEvent.getName().equals("build-teams")) {
			try {
				AudioChannel audioChannel = member.getVoiceState().getChannel();
				VoiceChannel voiceChannel = guild.getVoiceChannelById(audioChannel.getId());
				buildTeams(voiceChannel);
			} catch (Exception e) {
				slashEvent.reply("`Error: You need to join a voice channel first!`").complete();
			}
		}
		if (slashEvent.getName().equals("stats")) {
			buildStatReport();
		}
		if (slashEvent.getName().equals("edit-team")) {
			editTeam(slashEvent, guild, textChannelId);
		}
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		this.buttonEvent = event;
		this.member = buttonEvent.getMember();
		this.guild = buttonEvent.getGuild();
		this.textChannelId = buttonEvent.getChannel().getId();
		this.guildStatsPath = PATH + guild.getId() + ".txt";
		this.eventTextChannel = buttonEvent.getChannel().asTextChannel();

		if (buttonEvent.getComponentId().equals("previouspage")) {
			buttonEvent.reply("Going to previous...").setEphemeral(false).complete().deleteOriginal().complete();
			applyPagination(-1);
		}
		if (buttonEvent.getComponentId().equals("nextpage")) {
			buttonEvent.reply("Going to next...").setEphemeral(false).complete().deleteOriginal().complete();
			applyPagination(1);
		}
		if (buttonEvent.getComponentId().equals("cancel")) {
			buttonEvent.reply("Canceling ...").complete();
			buttonCancelMatch();
		}
		if (buttonEvent.getComponentId().equals("winner")) {
			TextInput teamnumberInput = TextInput.create("teamnumber", "Team Number", TextInputStyle.SHORT)
					.setPlaceholder("Team Number").setId("teamnumber").setLabel("Team Number").setMinLength(0)
					.setMaxLength(5).setRequired(true).build();
			Modal modal = Modal.create("winnerPopup", "Define the winner").addActionRows(ActionRow.of(teamnumberInput))
					.build();
			buttonEvent.replyModal(modal).complete();
		}

	}

	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		this.modalEvent = event;
		this.member = modalEvent.getMember();
		this.guild = modalEvent.getGuild();
		this.textChannelId = modalEvent.getChannel().getId();
		this.guildStatsPath = PATH + guild.getId() + ".txt";
		this.eventTextChannel = modalEvent.getChannel().asTextChannel();

		if (modalEvent.getModalId().equals("winnerPopup")) {
			buttonCalculateWinner();
		}
	}

	private void applyPagination(int i) {
		try {
			int destinationPage = 0;
			MessageEmbed oldEmbed = buttonEvent.getMessage().getEmbeds().get(0);
			Integer pageNumber = Integer.parseInt(oldEmbed.getFooter().getText().replace("Page: ", ""));

			String[] rows = new Reader().readFileAsString(guildStatsPath).split("\n");

			int availablePages = (int) Math.ceil((0.0d + rows.length) / MEMBERS_PER_PAGE);

			if (pageNumber == 1 && i == -1)
				destinationPage = availablePages;
			else if (pageNumber == availablePages && i == 1)
				destinationPage = 1;
			else
				destinationPage = pageNumber + i;

			EmbedBuilder newEmbed = editEmbed(destinationPage);
			buttonEvent.getMessage().editMessageEmbeds(newEmbed.build()).complete();
		} catch (Exception e) {
			eventTextChannel.sendMessage("ERROR: `" + e.getMessage() + "`");
		}
	}

	private EmbedBuilder editEmbed(int destinationPage) {
		try {
			String[] members = new Reader().readFileAsString(guildStatsPath).split("\n");
			EmbedBuilder embed = getEmebed(null, members, destinationPage);
			return embed;
		} catch (Exception e) {
			return null;
		}

	}

	// Method triggered by the command /edit-team
	// Changes position of two players in different teams
	private void editTeam(SlashCommandInteractionEvent event, Guild guild, String textChannelId) {
		try {
			event.reply("Editing teams...").complete();
			List<Member> members = new ArrayList<>();

			members.add(event.getOption("player-one").getAsMember());
			members.add(event.getOption("player-two").getAsMember());

			String channelIdOfPlayerOne = getVoiceChannelIdOfMember(guild, members, 0);
			String channelIdOfPlayerTwo = getVoiceChannelIdOfMember(guild, members, 1);

			moveMember(guild, members, 0, guild.getVoiceChannelById(channelIdOfPlayerTwo), textChannelId);
			moveMember(guild, members, 1, guild.getVoiceChannelById(channelIdOfPlayerOne), textChannelId);

			String path = PATH + guild.getId() + "-" + event.getOption("matchnumber").getAsLong() + ".txt";

			String matchFile = new Reader().readFileAsString(path);

			matchFile = matchFile.replace(members.get(0).getId(), "player2-placeholder");
			matchFile = matchFile.replace(members.get(1).getId(), "player1-placeholder");

			matchFile = matchFile.replace("player2-placeholder", members.get(1).getId());
			matchFile = matchFile.replace("player1-placeholder", members.get(0).getId());

			new Writer().writeToFile(path, matchFile);

			guild.getTextChannelById(textChannelId).sendMessage("Edited teams!").queue();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	// Method returning the id of a voice channel a user is in while executing a
	// command
	private String getVoiceChannelIdOfMember(Guild guild, List<Member> members, int i) {
		AudioChannel audioChannel = members.get(i).getVoiceState().getChannel();
		VoiceChannel voiceChannel = guild.getVoiceChannelById(audioChannel.getId());
		return voiceChannel.getId();
	}

	// Method to cancel a match triggered by the command /cancel
	// Cancels a match by match number

	private void buttonCancelMatch() {
		MessageEmbed oldEmbed = buttonEvent.getMessage().getEmbeds().get(0);
		String matchNumber = oldEmbed.getDescription().replace("Match number: ", "").replace("`", "").replaceAll("\n.*",
				"");
		String path = PATH + guild.getId() + "-" + matchNumber + ".txt";
		String voicecallPath = PATH + "vc-" + guild.getId() + "-" + matchNumber + ".txt";

		new Reader().deleteFile(path);
		guild.getTextChannelById(textChannelId).sendMessage("Canceled!").queue();
		moveAllMembersBack(guild, voicecallPath, textChannelId);
	}

	// Method triggered by the command /stats
	// Builds an embed that either creates a report of all guild members or one
	// selected user
	private void buildStatReport() {
		try {
			slashEvent.reply("Building report...").complete();
			Member mentionedMember = null;
			if (slashEvent.getOption("member") != null) {
				mentionedMember = slashEvent.getOption("member").getAsMember();
			}

			String[] members = new Reader().readFileAsString(guildStatsPath).split("\n");

			Button buttonPrevious = Button.primary("previouspage", "Previous Page");
			Button buttonNext = Button.primary("nextpage", "Next Page");
			EmbedBuilder embed = getEmebed(mentionedMember, members, 1);

			guild.getTextChannelById(textChannelId).sendMessageEmbeds(embed.build())
					.addActionRow(buttonPrevious, buttonNext).queue();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	private EmbedBuilder getEmebed(Member mentionedMember, String[] members, int page) {
		EmbedBuilder embed = createEmbedWithDefaults(
				"===== :trophy::trophy::trophy: Stats :trophy::trophy::trophy: =====");

		List<String> users = new ArrayList<>();
		List<String> wins = new ArrayList<>();
		List<String> loses = new ArrayList<>();
		List<Member> allGuildMembers = guild.getMembers();

		int membersOnPage = 0;
		double ignoreMembers = (page - 1) * MEMBERS_PER_PAGE;
		for (int i = 0; i < members.length; i++) {
			String[] memberStats = members[i].split(",");
			String percentage = " (" + Math.round(Double.parseDouble(memberStats[1])
					/ (Double.parseDouble(memberStats[1]) + Double.parseDouble(memberStats[2])) * 100) + "%)";
			if (mentionedMember != null) {
				if (mentionedMember.getId().equals(memberStats[0])) {
					users.add(mentionedMember.getUser().getName() + percentage);
					wins.add(memberStats[1]);
					loses.add(memberStats[2]);
				}
			} else {
				try {
					for (int j = 0; j < allGuildMembers.size(); j++) {
						if (allGuildMembers.get(j).getId().equals(memberStats[0]) && membersOnPage < MEMBERS_PER_PAGE) {
							if (ignoreMembers > 0) {
								ignoreMembers--;
							} else {
								membersOnPage++;
								users.add(allGuildMembers.get(j).getUser().getName() + percentage);
								wins.add(memberStats[1]);
								loses.add(memberStats[2]);
							}
						}
					}
				} catch (Exception e) {
				}
			}
		}
		Field user = new Field("User", String.join("\n", users), true);
		Field win = new Field("Wins", String.join("\n", wins), true);
		Field loss = new Field("Loses", String.join("\n", loses), true);

		embed.addField(user);
		embed.addField(win);
		embed.addField(loss);
		embed.setFooter("Page: " + page);
		return embed;
	}

	// method triggered by the command /win
	// Defines the winner and sums up the statistics

	private void buttonCalculateWinner() {
		try {

			MessageEmbed oldEmbed = modalEvent.getMessage().getEmbeds().get(0);
			String matchNumber = oldEmbed.getDescription().replace("Match number: ", "").replace("`", "")
					.replaceAll("\nTotal teams:.*", "");
			int maxTeams = Integer.parseInt(oldEmbed.getDescription().replaceAll(".*\nTotal teams: ", ""));

			modalEvent.getValue("teamnumber").getAsString();

			int winnerteam = Integer.parseInt(modalEvent.getValue("teamnumber").getAsString());
			String matchPath = PATH + guild.getId() + "-" + matchNumber + ".txt";
			String voicecallPath = PATH + "vc-" + guild.getId() + "-" + matchNumber + ".txt";

			if (!new Reader().fileExists(matchPath)) {
				modalEvent.reply("No match found for matchnumber: `" + matchNumber + "`").complete();
				return;
			}
			if (maxTeams < winnerteam && winnerteam > 0) {
				modalEvent.reply("`ERROR: The team number is higher than the total amount of teams!`").complete();
				return;
			}

			modalEvent.reply("Creating statistics...").complete();

			if (!new Reader().fileExists(guildStatsPath)) {
				new Writer().writeToFile(guildStatsPath, "");
			}

			manageWinner(winnerteam, matchPath, voicecallPath);

		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}

	}

	private void manageWinner(int winnerteam, String matchPath, String voicecallPath) throws Exception {
		String[] memberIds = new Reader().readFileAsString(matchPath).split("\n");
		int index = 1;
		for (String teams : memberIds) {
			if (!teams.equals("")) {
				String[] teamMemberIds = teams.split(",");
				int win = 0;
				int loss = 0;
				if (index == winnerteam) {
					win = 1;
				} else {
					loss = 1;
				}
				for (String memberId : teamMemberIds) {
					boolean found = false;
					String newEntry = "";
					String[] guildMemberStats = new Reader().readFileAsString(guildStatsPath).split("\n");
					for (String memberStats : guildMemberStats) {
						if (!memberStats.equals("")) {
							if (memberStats.startsWith(memberId)) {
								found = true;
								String[] stats = memberStats.split(",");
								int memberWins = Integer.parseInt(stats[1]) + win;
								int memberLoss = Integer.parseInt(stats[2]) + loss;
								newEntry = newEntry + memberId + "," + memberWins + "," + memberLoss + "\n";
							} else {
								newEntry = newEntry + memberStats + "\n";
							}
						}
					}
					if (!found) {
						newEntry = newEntry + memberId + "," + win + "," + loss + "\n";
					}
					new Writer().writeToFile(guildStatsPath, newEntry.replace("\n\n", "\n"));
				}
			}
			index++;
		}
		guild.getTextChannelById(textChannelId).sendMessage("Saved statistics!").queue();
		moveAllMembersBack(guild, voicecallPath, textChannelId);
		new Reader().deleteFile(matchPath);
	}

	// Method executed after a /cancel or /win
	// Moves all the members back to the default voice channel as long as they are
	// still draggable
	private void moveAllMembersBack(Guild guild, String voicecallPath, String textChannelId) {
		try {
			String[] rows = new Reader().readFileAsString(voicecallPath).split("\n");
			String[] vcs = rows[0].split(",");
			String originalVoiceChannelId = rows[1];
			String categoryId = rows[2];

			for (String vc : vcs) {
				List<Member> members = getShuffeledHumansInChannel(guild.getVoiceChannelById(vc));
				for (int i = 0; i < members.size(); i++) {
					moveMember(guild, members, i, guild.getVoiceChannelById(originalVoiceChannelId), textChannelId);
				}
			}
			deleteCreatedItems(guild, vcs, categoryId, textChannelId);
			new Reader().deleteFile(voicecallPath);
		} catch (Exception e) {
			// guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " +
			// e.getMessage() + "`").queue();
			new Reader().deleteFile(voicecallPath);
		}
	}

	// Method triggered by the command /build-teams
	// Creates the number of defined teams. Creates the Voice-Channels and drags the
	// users into them.
	private void buildTeams(VoiceChannel voiceChannel) {
		try {
			slashEvent.reply("Creating Teams for Members!").complete();
			String originalVoiceCannelId = voiceChannel.getId();
			List<Member> members = getShuffeledHumansInChannel(voiceChannel);
			int teams = slashEvent.getOption("teams").getAsInt();
			int memberCount = members.size();
			double memberPerTeamFloor = Math.floor(memberCount / teams);
			double memberPerTeamCeiling = Math.ceil(memberCount / (teams + 0.0));
			double memberLeft = memberCount % teams;
			long matchNumber = System.currentTimeMillis();
			boolean createChannels = slashEvent.getOption("create-channels").getAsBoolean();

			List<String> createdVoiceChannels = new ArrayList<>();
			List<List<String>> memberIds = new ArrayList<>();

			Category category = null;
			String categoryId = "";

			if (createChannels) {
				String categoryName = createCategoryName(memberPerTeamCeiling, teams);
				category = guild.createCategory(categoryName).complete();
				categoryId = category.getId();
			}

			int memberIndex = 0;
			for (int i = 0; i < teams; i++) {
				List<String> teamMember = new ArrayList<>();
				VoiceChannel teamVoiceChannel = null;
				if (createChannels) {
					if (slashEvent.getOption("limit").getAsBoolean()) {
						teamVoiceChannel = guild.createVoiceChannel("Team" + (i + 1), category)
								.setUserlimit((int) memberPerTeamCeiling).complete();
						createdVoiceChannels.add(teamVoiceChannel.getId());
					} else {
						teamVoiceChannel = guild.createVoiceChannel("Team" + (i + 1), category).complete();
						createdVoiceChannels.add(teamVoiceChannel.getId());
					}
				}

				if (memberLeft == 0) {
					for (int j = 0; j < memberPerTeamFloor; j++) {
						if (createChannels)
							moveMember(guild, members, memberIndex, teamVoiceChannel, textChannelId);
						teamMember.add(members.get(memberIndex).getId());
						memberIndex++;
					}
				} else {
					for (int j = 0; j < memberPerTeamFloor; j++) {
						if (createChannels)
							moveMember(guild, members, memberIndex, teamVoiceChannel, textChannelId);
						teamMember.add(members.get(memberIndex).getId());
						memberIndex++;
					}
					if (memberLeft != 0) {
						if (createChannels)
							moveMember(guild, members, memberIndex, teamVoiceChannel, textChannelId);
						teamMember.add(members.get(memberIndex).getId());
						memberIndex++;
						memberLeft--;
					}
				}
				memberIds.add(teamMember);
			}
			createMatchFile(guild, textChannelId, matchNumber, memberIds);
			createEmbed(guild, textChannelId, memberIds, matchNumber);
			createVoicecallFile(guild, memberIds, createdVoiceChannels, matchNumber, textChannelId,
					originalVoiceCannelId, categoryId);

		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	// Creates a temporary file about the created voice channels and category and
	// the information about
	// where to move the users after /cancel or /win (back to the default voice
	// channel).
	private void createVoicecallFile(Guild guild, List<List<String>> memberIds, List<String> createdVoiceChannels,
			long matchNumber, String textChannelId, String originalVoiceCannelId, String categoryId) {
		try {
			String voicecallPath = PATH + "vc-" + guild.getId() + "-" + matchNumber + ".txt";
			String vcs = String.join(",", createdVoiceChannels);
			vcs = vcs + "\n" + originalVoiceCannelId + "\n" + categoryId + "\n";

			new Writer().writeToFile(voicecallPath, vcs);
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	private void createMatchFile(Guild guild, String textChannelId, long matchNumber, List<List<String>> memberIds) {
		String path = PATH + guild.getId() + "-" + matchNumber + ".txt";
		String row = "";

		for (List<String> teamMemberIds : memberIds) {
			row = row + String.join(",", teamMemberIds);
			row = row + "\n";
		}
		try {
			new Writer().writeToFile(path, row);
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	// Method creates an embed displaying the team constellation
	private void createEmbed(Guild guild, String textChannelId, List<List<String>> memberIds, long matchNumber) {
		try {
			EmbedBuilder embed = createEmbedWithDefaults("=============== Teams ===============");
			embed.setDescription("Match number: `" + matchNumber + "`\nTotal teams: " + memberIds.size());
			int endl = 1;
			for (List<String> teamMember : memberIds) {
				List<String> names = new ArrayList<>();
				for (String id : teamMember) {
					names.add(guild.getMemberById(id).getUser().getName());
				}
				Field team = new Field("Team" + endl, String.join("\n", names), true);
				embed.addField(team);
				endl++;
			}
			Button buttonWinner = Button.primary("winner", "Define winner");
			Button buttonCancel = Button.primary("cancel", "Cancel match");
			guild.getTextChannelById(textChannelId).sendMessageEmbeds(embed.build())
					.addActionRow(buttonWinner, buttonCancel).queue();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	// Method to move one member of a member list by index into a defined
	// Voice-Channel by id
	private void moveMember(Guild guild, List<Member> members, int memberIndex, VoiceChannel teamVoiceChannel,
			String textChannelId) {
		try {
			guild.moveVoiceMember(members.get(memberIndex), teamVoiceChannel).complete();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	// Deleted method for the voice-channel and category created after the
	// /build-team command
	// Is called by the /win and /cancel command
	private void deleteCreatedItems(Guild guild, String[] voiceChannels, String categoryId, String textChannelId) {
		try {
			for (String id : voiceChannels) {
				guild.getVoiceChannelById(id).delete().queue();
			}
			guild.getCategoryById(categoryId).delete().queue();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	// Randomize method to shuffle the list of members
	private List<Member> getShuffeledHumansInChannel(VoiceChannel channel) {
		List<Member> members = new ArrayList<>();
		for (Member m : channel.getMembers()) {
			if (!m.getUser().isBot()) {
				members.add(m);
			}
		}
		Collections.shuffle(members);
		return members;
	}

	// Method to generate the string for the created category
	private String createCategoryName(double memberPerTeam, int teams) {
		String memberPerTeamString = memberPerTeam + "";
		String name = "";
		while (teams > 0) {
			name = name + memberPerTeamString.replace(".0", "");
			if (teams > 1) {
				name = name + "v";
			}
			teams--;
		}
		return name;
	}

	// Default embed configuration
	private EmbedBuilder createEmbedWithDefaults(String title) {
		EmbedBuilder embed = new EmbedBuilder();

		embed.setTitle(title);
		embed.setColor(Color.YELLOW);
		embed.setAuthor("Bot by: Duck#4303");

		return embed;
	}

}
