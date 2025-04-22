package fr.nivcoo.challenges.command.commands;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.command.CCommand;
import fr.nivcoo.utilsz.config.Config;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeleteDatasCMD implements CCommand {

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("delete_datas");
    }

    @Override
    public String getPermission() {
        return "challenges.command.delete_datas";
    }

    @Override
    public String getUsage() {
        return "delete_datas";
    }

    @Override
    public String getDescription() {
        return "Réinitialise entièrement le système de challenges et vide tous les scores (local et réseau).";
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

    @Override
    public void execute(Challenges challenges, CommandSender sender, String[] args) {
        Config config = challenges.getConfiguration();

        challenges.getCacheManager().resetAllData();

        sender.sendMessage(config.getString("messages.commands.success_delete_datas"));
    }

    @Override
    public List<String> tabComplete(Challenges plugin, CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
