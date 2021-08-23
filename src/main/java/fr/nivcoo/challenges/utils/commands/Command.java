package fr.nivcoo.challenges.utils.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public interface Command {

	/**
     * Get the aliases of the sub command.
     */
    List<String> getAliases();

    /**
     * Get the required permission to use the sub command.
     * If no permission is required, can be empty.
     */
    String getPermission();

    /**
     * Get the usage of the sub command.
     * @param locale The locale of the player.
     */
    String getUsage();

    /**
     * Get the description of the sub command.
     * @param locale The locale of the player.
     */
    String getDescription();

    /**
     * Get the minimum arguments required for the command.
     * For example, the command /is example PLAYER_NAME has 2 arguments.
     */
    int getMinArgs();

    /**
     * Get the maximum arguments required for the command.
     * For example, the command /is example PLAYER_NAME has 2 arguments.
     */
    int getMaxArgs();

    /**
     * Can the command be executed from console?
     * If true, sender cannot be casted directly into a player. Otherwise, it can be.
     */
    boolean canBeExecutedByConsole();

    /**
     * The method to be executed when the command is running.
     * @param plugin The instance of the plugin.
     * @param sender The sender who ran the command.
     * @param args The arguments of the command.
     */
    void execute(JavaPlugin plugin, CommandSender sender, String[] args);


}