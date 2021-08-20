package fr.nivcoo.challenges.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.utils.Config;
import net.md_5.bungee.api.ChatColor;

public class Commands implements CommandExecutor {
	private Challenges challenges;
	private Config config;

	public Commands() {
		challenges = Challenges.get();
		config = challenges.getConfiguration();

	}

	public void help(CommandSender sender) {

		if (sender.hasPermission("challenges.commands")
				&& (sender.hasPermission("challenges.start") || sender.hasPermission("challenges.stop"))) {
			sender.sendMessage(ChatColor.GRAY + "§m------------------" + ChatColor.DARK_GRAY + "[" + ChatColor.GOLD
					+ "Help Panel" + ChatColor.DARK_GRAY + "]" + ChatColor.GRAY + "§m------------------");
			if (sender.hasPermission("challenges.start"))
				sender.sendMessage(ChatColor.GOLD + "/clgs start " + ChatColor.YELLOW + "start a challenge !");
			if (sender.hasPermission("challenges.stop"))
				sender.sendMessage(ChatColor.GOLD + "/clgs stop " + ChatColor.YELLOW + "stop the current challenge !");
			if (sender.hasPermission("challenges.start_interval"))
				sender.sendMessage(
						ChatColor.GOLD + "/clgs start_interval " + ChatColor.YELLOW + "start challenge interval !");
			if (sender.hasPermission("challenges.stop_interval"))
				sender.sendMessage(
						ChatColor.GOLD + "/clgs stop_interval " + ChatColor.YELLOW + "stop challenge interval !");
			sender.sendMessage(ChatColor.GRAY + "§m----------------------------------------------");

		}

		return;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String msg, String[] args) {
		String unknownMessage = config.getString("messages.commands.no_permission");
		if (cmd.getName().equalsIgnoreCase("clgs")) {

			if (args.length == 0) {
				if (sender.hasPermission("challenges.commands")) {
					help(sender);
					return true;
				}
			} else if (args.length >= 1) {

				if (args[0].equalsIgnoreCase("start") && sender.hasPermission("challenges.start")) {
					challenges.getChallengesManager().startChallenge();
					sender.sendMessage(config.getString("messages.commands.success_start"));
					return true;
				} else if (args[0].equalsIgnoreCase("stop") && sender.hasPermission("challenges.stop")) {
					challenges.getChallengesManager().stopCurrentChallenge();
					sender.sendMessage(config.getString("messages.commands.success_stop"));
					return true;
				} else if (args[0].equalsIgnoreCase("start_interval")
						&& sender.hasPermission("challenges.start_interval")) {
					challenges.getChallengesManager().startChallengeInterval();
					sender.sendMessage(config.getString("messages.commands.success_start_interval"));
					return true;
				} else if (args[0].equalsIgnoreCase("stop_interval")
						&& sender.hasPermission("challenges.stop_interval")) {
					challenges.getChallengesManager().stopChallengeTasks();
					sender.sendMessage(config.getString("messages.commands.success_stop_interval"));
					return true;
				} else {
					sender.sendMessage(unknownMessage);
					return false;
				}
			} else {
				sender.sendMessage(unknownMessage);
				return false;
			}
		}

		return false;
	}

}
