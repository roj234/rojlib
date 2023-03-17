package ilib.command.sub;

import ilib.command.MasterCommand;
import roj.collect.MyHashMap;

import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CmdSubCmd extends AbstractSubCommand {
	private final MyHashMap<String, ISubCommand> subCommands = new MyHashMap<>();
	private TextComponentString helpComponent;
	private String canUseCommands;

	private final String commandName;
	private String help = "该指令没有提供帮助";

	public CmdSubCmd(String name) {
		this.commandName = name;
	}

	@Override
	public String getHelp() {
		return this.help;
	}

	@Override
	public String getName() {
		return this.commandName;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (helpComponent == null) helpComponent = new TextComponentString("/" + getParent().getName() + ' ' + getName() + " help <name>");
		if (args.length >= 1) {
			String s = args[0];
			ISubCommand command = subCommands.get(s);
			if (command != null) {
				command.execute(server, sender, MasterCommand.removeFirst(args));
				return;
			} else if ("help".equals(s)) {
				if (args.length == 1) {
					sender.sendMessage(helpComponent);
				} else {
					command = subCommands.get(args[1]);
					if (command != null) sender.sendMessage(new TextComponentTranslation(command.getHelp()));
				}
				return;
			}
		}
		if (canUseCommands == null) {
			if (subCommands.isEmpty()) throw new CommandException("没有子命令! 这是一个BUG!");
			StringBuilder sb = new StringBuilder("/");
			sb.append(getParent().getName());
			sb.append(' ');
			sb.append(getName());
			sb.append(" <");
			for (String str : subCommands.keySet()) {
				sb.append(str);
				sb.append('/');
			}
			sb.append("help");
			sb.append('>');
			canUseCommands = sb.toString();
		}
		throw new WrongUsageException(canUseCommands);
	}

	@Override
	public CmdSubCmd setParent(ICommand parent) {
		super.setParent(parent);
		for (ISubCommand cmd : subCommands.values()) {
			cmd.setParent(parent);
		}
		return this;
	}

	@Override
	public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, subCommands.keySet());
		} else if (args.length > 1) {
			String s = args[0];
			ISubCommand command = subCommands.get(s);
			return command == null ? ("help".equals(s) ? CommandBase.getListOfStringsMatchingLastWord(args, subCommands.keySet()) : EMPTY) : command.getTabCompletionOptions(server, sender,
																																											 MasterCommand.removeFirst(
																																												 args), pos);
		} else {
			return EMPTY;
		}
	}

	public boolean isUsernameIndex(String[] args, int index) {
		if (args.length < 1) {
			return false;
		} else {
			String s = args[0];
			ISubCommand command = subCommands.get(s);
			return command != null && command.isUsernameIndex(MasterCommand.removeFirst(args), index - 1);
		}
	}

	public CmdSubCmd register(ISubCommand command) {
		subCommands.put(command.getName(), command);
		command.setParent(this.getParent());
		return this;
	}

	public CmdSubCmd setHelp(String help) {
		this.help = help;
		return this;
	}
}
