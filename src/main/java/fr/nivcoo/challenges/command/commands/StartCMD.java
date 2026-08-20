package fr.nivcoo.challenges.command.commands;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.platform.bukkit.commands.BukkitCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class StartCMD implements BukkitCommand {
    @Override public List<String> getAliases() { return Collections.singletonList("start"); }
    @Override public String getPermission() { return "challenges.command.start"; }
    @Override public String getUsage() { return "start"; }
    @Override public String getDescription() { return null; }
    @Override public int getMinArgs() { return 1; }
    @Override public int getMaxArgs() { return 1; }
    @Override public boolean canBeExecutedByConsole() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Challenges plugin = Challenges.get();
        if (plugin.getChallengesManager().startChallenge()) {
            sender.sendMessage(plugin.cfg().messages.commands.successStart);
        }
    }

    @Override public List<String> tabComplete(CommandSender sender, String label, String[] args) { return List.of(); }
}
