package fr.nivcoo.challenges.command.commands;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.command.CCommand;
import fr.nivcoo.utilsz.config.Config;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StopCMD implements CCommand {

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("stop");
    }

    @Override
    public String getPermission() {
        return "challenges.command.stop";
    }

    @Override
    public String getUsage() {
        return "stop";
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
        challenges.getChallengesManager().stopCurrentChallenge();
        sender.sendMessage(config.getString("messages.commands.success_stop"));
    }

    @Override
    public List<String> tabComplete(Challenges plugin, CommandSender sender, String[] args) {
        return new ArrayList<>();
    }

}
