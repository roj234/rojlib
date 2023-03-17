package ilib.command;

import ilib.command.sub.AbstractSubCommand;
import ilib.command.sub.ISubCommand;
import roj.collect.MyHashMap;

import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/2 23:54
 */
public final class MasterCommand extends CommandBase {
	public final String name;
	public final int permission;

	private final MyHashMap<String, ISubCommand> subCommands = new MyHashMap<>();

	private List<String> aliases;
	private TextComponentString helpComponent;
	private String canUseCommands;

	public static ICommandListener listener;

	public MasterCommand(String name, int level) {
		this.name = name;
		this.permission = level;
	}

	@Nonnull
	public String getName() {
		return name;
	}

	@Nonnull
	public String getUsage(@Nonnull ICommandSender sender) {
		return "command.mi." + name + ".usage";
	}

	@Nonnull
	public List<String> getAliases() {
		return this.aliases == null ? AbstractSubCommand.EMPTY : this.aliases;
	}

	public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
		if (helpComponent == null) helpComponent = new TextComponentString("/" + getName() + " help <name>");
		if (args.length >= 1) {
			String s = args[0];
			ISubCommand command = subCommands.get(s);
			if (command != null) {
				command.execute(server, sender, removeFirst(args));
				return;
			} else if ("help".equals(s)) {
				if (args.length == 1) {
					sender.sendMessage(new TextComponentString(getName() + " help <name>"));
				} else {
					command = subCommands.get(args[1]);
					if (command != null) sender.sendMessage(new TextComponentTranslation(command.getHelp()));
				}
				return;
			}
		}
		if (canUseCommands == null) {
			if (subCommands.size() == 0) throw new CommandException("没有子命令! 这是一个BUG!");
			StringBuilder sb = new StringBuilder("/" + getName() + " <");
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

	public static void setCommandListener(ICommandListener _listener) {
		listener = _listener;
	}

	@Override
	public int getRequiredPermissionLevel() {
		return this.permission;
	}

	//public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
	//    return IGNORE_PERMISSION || super.checkPermission(server, sender);
	//}

	@Nonnull
	public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, String[] args, @Nullable BlockPos pos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, subCommands.keySet());
		} else if (args.length > 1) {
			String s = args[0];
			ISubCommand command = subCommands.get(s);
			return command == null ? ("help".equals(s) ? getListOfStringsMatchingLastWord(args, subCommands.keySet()) : AbstractSubCommand.EMPTY) : command.getTabCompletionOptions(server, sender,
																																													removeFirst(args),
																																													pos);
		} else {
			return AbstractSubCommand.EMPTY;
		}
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		if (args.length < 1) {
			return false;
		} else {
			String s = args[0];
			ISubCommand command = subCommands.get(s);
			return command != null && command.isUsernameIndex(removeFirst(args), index - 1);
		}
	}

	public MasterCommand register(ISubCommand cmd) {
		if (cmd == null) return this;
		subCommands.put(cmd.getName(), cmd);
		cmd.setParent(this);
		return this;
	}

	public MasterCommand aliases(String alias) {
		if (this.aliases == null) {
			this.aliases = new ArrayList<>();
			this.aliases.add(getName());
		}
		this.aliases.add(alias);
		return this;
	}

	public static String[] removeFirst(String[] args) {
		if (args.length < 2) {
			return new String[0];
		} else {
			String[] nArgs = new String[args.length - 1];
			System.arraycopy(args, 1, nArgs, 0, nArgs.length);
			return nArgs;
		}
	}
}
