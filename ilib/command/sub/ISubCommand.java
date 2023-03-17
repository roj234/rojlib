package ilib.command.sub;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface ISubCommand {
	String getName();

	String getHelp();

	ISubCommand setParent(ICommand command);

	ICommand getParent();

	void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;

	List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos);

	boolean isUsernameIndex(String[] var1, int var2);
}
