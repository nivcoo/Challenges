package fr.nivcoo.challenges.command.commands;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.platform.bukkit.commands.BukkitCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class ReloadCMD implements BukkitCommand {
    @Override public List<String> getAliases() { return Collections.singletonList("reload"); }
    @Override public String getPermission() { return "challenges.command.reload"; }
    @Override public String getUsage() { return "reload"; }
    @Override public String getDescription() { return null; }
    @Override public int getMinArgs() { return 1; }
    @Override public int getMaxArgs() { return 1; }
    @Override public boolean canBeExecutedByConsole() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Challenges plugin = Challenges.get();
        if (plugin.getChallengesManager().isReloadUnsafe()) {
            sender.sendMessage("§cLe rechargement est bloqué pendant un challenge actif afin de ne perdre aucun progrès.");
            return;
        }
        plugin.reload();
        sender.sendMessage(plugin.cfg().messages.commands.successReload);
    }

    @Override public List<String> tabComplete(CommandSender sender, String label, String[] args) { return List.of(); }
}
