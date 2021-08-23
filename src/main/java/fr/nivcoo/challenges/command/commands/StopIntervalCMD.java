package fr.nivcoo.challenges.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.command.CCommand;
import fr.nivcoo.challenges.utils.Config;

public class StopIntervalCMD implements CCommand {

	@Override
	public List<String> getAliases() {
		return Arrays.asList("stop_interval");
	}

	@Override
	public String getPermission() {
		return "challenges.command.stop_interval";
	}

	@Override
	public String getUsage() {
		return "stop_interval";
	}

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public int getMinArgs() {
		return 1;
	}

	@Override
	public int getMaxArgs() {
		return 1;
	}

	@Override
	public boolean canBeExecutedByConsole() {
		return true;
	}

	public void execute(Challenges challenges, CommandSender sender, String[] args) {
		Config config = challenges.getConfiguration();
		challenges.getChallengesManager().stopChallengeTasks();
		sender.sendMessage(config.getString("messages.commands.success_stop_interval"));
	}

	@Override
	public List<String> tabComplete(Challenges plugin, CommandSender sender, String[] args) {
		return new ArrayList<>();
	}

}
