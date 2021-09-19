package fr.nivcoo.challenges.command.commands;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.command.CCommand;
import fr.nivcoo.challenges.utils.Config;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeleteDatasCMD implements CCommand {

    @Override
    public List<String> getAliases() {
        return Arrays.asList("delete_datas");
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
        challenges.getDatabase().clearDB();
        challenges.reload();
        sender.sendMessage(config.getString("messages.commands.success_delete_datas"));
    }

    @Override
    public List<String> tabComplete(Challenges plugin, CommandSender sender, String[] args) {
        return new ArrayList<>();
    }

}
