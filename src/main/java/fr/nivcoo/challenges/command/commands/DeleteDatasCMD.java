package fr.nivcoo.challenges.command.commands;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.ChallengeRole;
import fr.nivcoo.utilsz.platform.bukkit.commands.BukkitCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class DeleteDatasCMD implements BukkitCommand {
    @Override public List<String> getAliases() { return Collections.singletonList("delete_datas"); }
    @Override public String getPermission() { return "challenges.command.delete_datas"; }
    @Override public String getUsage() { return "delete_datas"; }
    @Override public String getDescription() { return "Réinitialise entièrement le système de challenges et vide tous les scores."; }
    @Override public int getMinArgs() { return 1; }
    @Override public int getMaxArgs() { return 1; }
    @Override public boolean canBeExecutedByConsole() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Challenges plugin = Challenges.get();
        if (plugin.getChallengesManager().role() != ChallengeRole.COORDINATOR) {
            sender.sendMessage("§cCette commande est réservée au serveur coordinateur.");
            return;
        }
        plugin.resetRankingAsync().whenComplete((ignored, error) -> {
            Runnable feedback = () -> {
                if (error == null) {
                    sender.sendMessage(plugin.cfg().messages.commands.successDeleteDatas);
                } else {
                    sender.sendMessage("§cLa réinitialisation des challenges a échoué. Consultez la console.");
                    plugin.getLogger().severe("Unable to reset challenge ranking: " + error.getMessage());
                }
            };
            if (org.bukkit.Bukkit.isPrimaryThread()) feedback.run();
            else org.bukkit.Bukkit.getScheduler().runTask(plugin, feedback);
        });
    }

    @Override public List<String> tabComplete(CommandSender sender, String label, String[] args) { return List.of(); }
}
