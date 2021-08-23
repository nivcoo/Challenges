package fr.nivcoo.challenges.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.command.CCommand;
import fr.nivcoo.challenges.utils.Config;

public class EndCMD implements CCommand {

	@Override
	public List<String> getAliases() {
		return Arrays.asList("end");
	}

	@Override
	public String getPermission() {
		return "challenges.command.end";
	}

	@Override
	public String getUsage() {
		return "end";
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
		challenges.getChallengesManager().finishChallenge();
		sender.sendMessage(config.getString("messages.commands.success_end"));
		
	}

	@Override
	public List<String> tabComplete(Challenges plugin, CommandSender sender, String[] args) {
		return new ArrayList<>();
	}

}
