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
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CommandListener extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		Member member = event.getMember();
		Guild guild = event.getGuild();
		String textChannelId = event.getChannel().getId();
		String guildStatsPath = "/matches/" + guild.getId() + ".txt";

		if (event.getName().equals("build-teams")) {
			try {
				AudioChannel audioChannel = member.getVoiceState().getChannel();
				VoiceChannel voiceChannel = guild.getVoiceChannelById(audioChannel.getId());
				buildTeams(event, guild, textChannelId, voiceChannel);
			} catch (Exception e) {
				event.reply("`Error: You need to join a voice channel first!`").complete();
			}
		}
		if (event.getName().equals("win")) {
			calculateWinner(event, guild, textChannelId, guildStatsPath);
		}
		if (event.getName().equals("cancel")) {
			cancelMatch(event, guild, textChannelId);
		}
		if (event.getName().equals("stats")) {
			buildStatReport(event, guild, textChannelId, guildStatsPath);
		}
		if (event.getName().equals("edit-team")) {
			editTeam(event, guild, textChannelId);
		}
	}

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
			
			String path = "/matches/" + guild.getId() + "-" + event.getOption("matchnumber").getAsLong() + ".txt";
			
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

	private String getVoiceChannelIdOfMember(Guild guild, List<Member> members, int i) {
		AudioChannel audioChannel = members.get(i).getVoiceState().getChannel();
		VoiceChannel voiceChannel = guild.getVoiceChannelById(audioChannel.getId());
		return voiceChannel.getId();
	}

	private void cancelMatch(SlashCommandInteractionEvent event, Guild guild, String textChannelId) {
		event.reply("Canceling match...").complete();
		String path = "/matches/" + guild.getId() + "-" + event.getOption("matchnumber").getAsLong() + ".txt";
		String voicecallPath = "/matches/vc-" + guild.getId() + "-" + event.getOption("matchnumber").getAsLong()
				+ ".txt";
		new Reader().deleteFile(path);
		guild.getTextChannelById(textChannelId).sendMessage("Canceled!").queue();
		moveAllMembersBack(guild, voicecallPath, textChannelId);
	}

	private void buildStatReport(SlashCommandInteractionEvent event, Guild guild, String textChannelId,
			String guildStatsPath) {
		try {
			event.reply("Building report...").complete();
			Member mentionedMember = null;
			if (event.getOption("member") != null) {
				mentionedMember = event.getOption("member").getAsMember();
			}

			String[] members = new Reader().readFileAsString(guildStatsPath).split("\n");
			EmbedBuilder embed = new EmbedBuilder();
			embed.setTitle("===== :trophy::trophy::trophy: Stats :trophy::trophy::trophy: =====");
			embed.setColor(Color.YELLOW);
			embed.setFooter("Bot by: Duck#4303");

			List<String> users = new ArrayList<>();
			List<String> wins = new ArrayList<>();
			List<String> loses = new ArrayList<>();
			List<Member> allGuildMembers = guild.getMembers();

			for (String member : members) {
				String[] memberStats = member.split(",");
				if (mentionedMember != null) {
					if (mentionedMember.getId().equals(memberStats[0])) {
						users.add(mentionedMember.getUser().getName());
						wins.add(memberStats[1]);
						loses.add(memberStats[2]);
					}
				} else {
					try {
						for (Member guildMember : allGuildMembers) {
							if (guildMember.getId().equals(memberStats[0])) {
								users.add(guildMember.getUser().getName());
								wins.add(memberStats[1]);
								loses.add(memberStats[2]);
							}
						}
					} catch (Exception e) {
					}
				}
			}
			Field user = new Field("User", users.toString().replaceAll("[*\\]\\[,]", "\n"), true);
			Field win = new Field("Wins", wins.toString().replaceAll("[*\\]\\[,]", "\n"), true);
			Field loss = new Field("Loses", loses.toString().replaceAll("[*\\]\\[,]", "\n"), true);

			embed.addField(user);
			embed.addField(win);
			embed.addField(loss);

			guild.getTextChannelById(textChannelId).sendMessageEmbeds(embed.build()).queue();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	private void calculateWinner(SlashCommandInteractionEvent event, Guild guild, String textChannelId,
			String guildStatsPath) {
		try {
			long matchNumber = event.getOption("matchnumber").getAsLong();
			int winnerteam = event.getOption("teamnumber").getAsInt();
			String matchPath = "/matches/" + guild.getId() + "-" + matchNumber + ".txt";
			String voicecallPath = "/matches/vc-" + guild.getId() + "-" + matchNumber + ".txt";

			if (!new Reader().fileExists(matchPath)) {
				event.reply("No match found under matchnumber: `" + matchNumber + "`").complete();
				return;
			}

			event.reply("Creating statistics...").complete();
			if (!new Reader().fileExists(guildStatsPath)) {
				new Writer().writeToFile(guildStatsPath, "");
			}

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
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}

	}

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
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	private void buildTeams(SlashCommandInteractionEvent event, Guild guild, String textChannelId,
			VoiceChannel voiceChannel) {
		try {
			event.reply("Creating Teams for Members!").complete();
			String originalVoiceCannelId = voiceChannel.getId();
			List<Member> members = getShuffeledHumansInChannel(voiceChannel);
			int teams = event.getOption("teams").getAsInt();
			int memberCount = members.size();
			double memberPerTeamFloor = Math.floor(memberCount / teams);
			double memberPerTeamCeiling = Math.ceil(memberCount / (teams + 0.0));
			double memberLeft = memberCount % teams;
			long matchNumber = System.currentTimeMillis();

			List<String> createdVoiceChannels = new ArrayList<>();
			List<List<String>> memberIds = new ArrayList<>();

			String categoryName = createCategoryName(memberPerTeamCeiling, teams);
			Category category = guild.createCategory(categoryName).complete();
			String categoryId = category.getId();

			int memberIndex = 0;
			for (int i = 0; i < teams; i++) {
				List<String> teamMember = new ArrayList<>();
				VoiceChannel teamVoiceChannel = null;
				if (event.getOption("limit").getAsBoolean()) {
					teamVoiceChannel = guild.createVoiceChannel("Team" + (i + 1), category)
							.setUserlimit((int) memberPerTeamCeiling).complete();
					createdVoiceChannels.add(teamVoiceChannel.getId());
				} else {
					teamVoiceChannel = guild.createVoiceChannel("Team" + (i + 1), category).complete();
					createdVoiceChannels.add(teamVoiceChannel.getId());
				}

				if (memberLeft == 0) {
					for (int j = 0; j < memberPerTeamFloor; j++) {
						moveMember(guild, members, memberIndex, teamVoiceChannel, textChannelId);
						teamMember.add(members.get(memberIndex).getId());
						memberIndex++;
					}
				} else {
					for (int j = 0; j < memberPerTeamFloor; j++) {
						moveMember(guild, members, memberIndex, teamVoiceChannel, textChannelId);
						teamMember.add(members.get(memberIndex).getId());
						memberIndex++;
					}
					if (memberLeft != 0) {
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

	private void createVoicecallFile(Guild guild, List<List<String>> memberIds, List<String> createdVoiceChannels,
			long matchNumber, String textChannelId, String originalVoiceCannelId, String categoryId) {
		try {
			String voicecallPath = "/matches/vc-" + guild.getId() + "-" + matchNumber + ".txt";
			String vcs = String.join(",", createdVoiceChannels);
			vcs = vcs + "\n" + originalVoiceCannelId + "\n" + categoryId + "\n";

			new Writer().writeToFile(voicecallPath, vcs);
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	private void createMatchFile(Guild guild, String textChannelId, long matchNumber, List<List<String>> memberIds) {
		String path = "/matches/" + guild.getId() + "-" + matchNumber + ".txt";
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

	private void createEmbed(Guild guild, String textChannelId, List<List<String>> memberIds, long matchNumber) {
		try {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setTitle("=============== Teams ===============");
			embed.setColor(Color.YELLOW);
			embed.setFooter("Bot by: Duck#4303");
			embed.setDescription("Match number: `" + matchNumber + "`");
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
			guild.getTextChannelById(textChannelId).sendMessageEmbeds(embed.build()).queue();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	private void moveMember(Guild guild, List<Member> members, int memberIndex, VoiceChannel teamVoiceChannel,
			String textChannelId) {
		try {
			guild.moveVoiceMember(members.get(memberIndex), teamVoiceChannel).complete();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

	private void deleteCreatedItems(Guild guild, String[] voiceChannels, String categoryId, String textChannelId) {
		try {
			Thread.sleep(2 * 1000);
			for (String id : voiceChannels) {
				guild.getVoiceChannelById(id).delete().complete();
			}
			guild.getCategoryById(categoryId).delete().complete();
		} catch (Exception e) {
			guild.getTextChannelById(textChannelId).sendMessage("`ERROR: " + e.getMessage() + "`").queue();
		}
	}

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

}
