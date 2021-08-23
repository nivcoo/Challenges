package fr.nivcoo.challenges.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.utils.commands.Command;

public interface CCommand extends Command {

	default void execute(JavaPlugin plugin, CommandSender sender, String[] args){
        execute((Challenges) plugin, sender, args);
    }
	
	void execute(Challenges plugin, CommandSender sender, String[] args);
}
