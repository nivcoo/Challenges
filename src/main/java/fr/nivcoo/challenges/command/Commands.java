package fr.nivcoo.challenges.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.utils.Config;

public class Commands implements CommandExecutor {
	private Challenges challenges;
	private Config config;

	public Commands() {
		challenges = Challenges.get();
		config = challenges.getConfiguration();
	}

	public void help(CommandSender sender) {

		if (sender.hasPermission("challenges.help")) {
			List<String> helpMessages = config.getStringList("messages.commands.help");
			int i = 0;
			String helpMessage = "";
			for (String m : helpMessages) {
				int startPermissionIndex = m.indexOf("{!");
				String permission = null;
				if (startPermissionIndex >= 0) {
					permission = m.substring(startPermissionIndex + 2, m.indexOf("}"));
				}
				if (permission == null || sender.hasPermission(permission)) {
					helpMessage += m.replace("{!" + permission + "}", "");
					if (helpMessages.size() - 1 != i)
						helpMessage += " \n";
				}
				i++;
			}
			sender.sendMessage(helpMessage);
		}

		return;

	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String msg, String[] args) {
		String unknownMessage = config.getString("messages.commands.no_permission");
		if (cmd.getName().equalsIgnoreCase("clgs")) {

			if (args.length == 0 && sender.hasPermission("challenges.help")) {
				help(sender);
				return true;
			} else if (args.length >= 1) {

				if (args[0].equalsIgnoreCase("start") && sender.hasPermission("challenges.commands.start")) {
					challenges.getChallengesManager().startChallenge();
					sender.sendMessage(config.getString("messages.commands.success_start"));
					return true;
				} else if (args[0].equalsIgnoreCase("stop") && sender.hasPermission("challenges.commands.stop")) {
					challenges.getChallengesManager().stopCurrentChallenge();
					sender.sendMessage(config.getString("messages.commands.success_stop"));
					return true;
				} else if (args[0].equalsIgnoreCase("end") && sender.hasPermission("challenges.commands.end")) {
					challenges.getChallengesManager().finishChallenge();
					sender.sendMessage(config.getString("messages.commands.success_end"));
					return true;
				} else if (args[0].equalsIgnoreCase("start_interval")
						&& sender.hasPermission("challenges.commands.start_interval")) {
					challenges.getChallengesManager().startChallengeInterval();
					sender.sendMessage(config.getString("messages.commands.success_start_interval"));
					return true;
				} else if (args[0].equalsIgnoreCase("stop_interval")
						&& sender.hasPermission("challenges.commands.stop_interval")) {
					challenges.getChallengesManager().stopChallengeTasks();
					sender.sendMessage(config.getString("messages.commands.success_stop_interval"));
					return true;
				} else if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("challenges.commands.reload")) {
					challenges.reload();
					sender.sendMessage(config.getString("messages.commands.success_reload"));
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
