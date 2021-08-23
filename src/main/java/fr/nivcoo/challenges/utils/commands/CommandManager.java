package fr.nivcoo.challenges.utils.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nivcoo.challenges.utils.Config;

public class CommandManager implements CommandExecutor {
	JavaPlugin plugin;
	private ArrayList<Command> commands;
	String unknownMessage = "Unknown command.";
	String globalCommand;
	String commandPermission;
	Config messages;

	public CommandManager(JavaPlugin plugin, Config messages, String globalCommand, String commandPermission) {
		this.plugin = plugin;
		commands = new ArrayList<Command>();
		this.messages = messages;
		this.globalCommand = globalCommand;
		this.commandPermission = commandPermission;
		plugin.getCommand(globalCommand).setExecutor(this);

	}

	public void addCommand(Command c) {
		commands.add(c);
	}

	public ArrayList<Command> getCommands() {
		return commands;
	}

	public Command getCommand(String arg) {
		for (Command c : getCommands()) {
			if (c.getAliases().contains(arg))
				return c;
		}
		return null;
	}

	public void help(CommandSender sender) {

		int i = 0;
		String helpMessage = "";
		List<String> helpMessages = messages.getStringList("messages.commands.help");
		for (String m : helpMessages) {
			int startPermissionIndex = m.indexOf("{!");
			String permission = null;
			if (startPermissionIndex >= 0) {
				permission = m.substring(startPermissionIndex + 2, m.indexOf("}"));
			}
			if (permission == null || sender.hasPermission(permission)) {
				helpMessage += m.replace("{!" + permission + "}", "");
				if (helpMessages.size() - 1 != i)
					helpMessage += " \n";
			}
			i++;
		}
		sender.sendMessage(helpMessage);

	}

	@Override
	public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase(globalCommand))
			return false;
		String noPermission = messages.getString("messages.commands.no_permission");
		if (args.length <= 0) {
			if (sender.hasPermission(commandPermission))
				help(sender);
			else
				sender.sendMessage(noPermission);
			return false;
		}
		Command command = getCommand(args[0]);
		if (command != null) {
			if (!(sender instanceof Player) && !command.canBeExecutedByConsole()) {
				sender.sendMessage("§cCan be executed only by players!");
				return false;
			}
			if (!command.getPermission().isEmpty() && !sender.hasPermission(command.getPermission())) {
				sender.sendMessage(noPermission);
				return false;
			}

			if (args.length < command.getMinArgs() || args.length > command.getMaxArgs()) {
				sender.sendMessage(messages.getString("messages.commands.incorrect_usage",
						globalCommand + " " + command.getUsage()));
				return false;
			}
			command.execute(plugin, sender, args);
		} else
			sender.sendMessage(noPermission);

		return false;
	}

}