package ilib.command;

import roj.collect.MyHashSet;

import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import net.minecraftforge.common.DimensionManager;

import java.util.Set;

/**
 * @author Roj234
 * @since 2021/5/22 14:44
 */
public final class CommandListenerIL extends CommandHandler implements ICommandListener {
	public final CommandHandler serverCmd;
	public final MinecraftServer server;

	public final ICommandSender NULL_SENDER = new ICommandSender() {
		@Override
		public String getName() {
			return "MI_NULL_SENDER";
		}

		@Override
		public boolean canUseCommand(int permissionLvl, String cmd) {
			return true;
		}

		@Override
		public World getEntityWorld() {
			return DimensionManager.getWorld(0);
		}

		public MinecraftServer getServer() {
			return CommandListenerIL.this.getServer();
		}
	};

	private static final Set<String> forbiddenCommands = new MyHashSet<>(32);

	public CommandListenerIL(MinecraftServer server) {
		this.server = server;
		serverCmd = (CommandHandler) server.commandManager;
		CommandBase.setCommandListener(this);
		MasterCommand.setCommandListener(this);
	}

	public void runCmdWithNull(String cmd) {
		executeCommand(NULL_SENDER, cmd);
	}

	public void runCmd(ICommandSender sender, String cmd) {
		executeCommand(sender, cmd);
	}

	public static void addBannedCmd(String name) {
		forbiddenCommands.add(name);
	}

	@Override
	public int executeCommand(ICommandSender sender, String rawCommand) {
		if (forbiddenCommands.contains(rawCommand)) {
			TextComponentTranslation text = new TextComponentTranslation("commands.generic.permission");
			text.getStyle().setColor(TextFormatting.RED);
			sender.sendMessage(text);
			return 0;
		}
		return serverCmd.executeCommand(sender, rawCommand);
	}

	@Override
	protected MinecraftServer getServer() {
		return this.server;
	}

	@Override
	public void notifyListener(ICommandSender sender, ICommand cmd, int i, String s, Object... objects) {
		((ICommandListener) serverCmd).notifyListener(sender, cmd, i, s, objects);
	}
}