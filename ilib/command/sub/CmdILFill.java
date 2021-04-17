package ilib.command.sub;

import com.google.common.base.Predicates;
import ilib.math.Arena;
import ilib.math.Section;
import ilib.math.SelectionCache;
import ilib.util.BlockHelper;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class CmdILFill extends AbstractSubCommand {
	@Override
	public String getName() {
		return "fill";
	}

	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException("commands.fill.usage");
		}

		// <block> <meta> keep <tile>
		// <block> <meta> destroy <tile>
		// <block> <meta> replace <to> <state> <tile>

		EntityPlayer player = getAsPlayer(sender);

		Block block = CommandBase.getBlockByText(sender, args[0]);

		sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, 0);

		Arena arena = SelectionCache.get(player.getUniqueID().getMostSignificantBits());
		if (arena == null) {
			throw new CommandException("command.ilib.sel.arena_not");
		}

		BlockPos start;
		BlockPos end;
		if (arena.isOK()) {
			Section sect = arena.toSection();
			start = sect.minBlock();
			end = sect.maxBlock();
		} else {
			throw new CommandException("command.ilib.sel.arena_not");
		}

		IBlockState state = args.length >= 2 ? CommandBase.convertArgToBlockState(block, args[1]) : block.getDefaultState();

		int i = (end.getX() - start.getX() + 1) * (end.getY() - start.getY() + 1) * (end.getZ() - start.getZ() + 1);

		if (i > 16777215) {
			throw new CommandException("commands.fill.tooManyBlocks", i, 16777215);
		}
		if (start.getY() < 0 || end.getY() >= 256) {
			throw new CommandException("commands.fill.outOfWorld");
		}

		World world = sender.getEntityWorld();

		NBTTagCompound tag = null;

		int category = 0;
		Predicate<IBlockState> replaceTo = null;
		Block replaceBlock = null;

		if (args.length >= 3) {
			switch (args[2]) {
				case "destroy":
					category = 2;
					tag = getNbtTagCompound(args, state, block, 3);
					break;
				case "keep":
					category = 1;
					tag = getNbtTagCompound(args, state, block, 3);
					break;
				case "replace":
					category = 0;
					if (args.length >= 4) {
						replaceBlock = CommandBase.getBlockByText(sender, args[3]);
						replaceTo = args.length > 4 ? ("*".equals(args[4]) ? Predicates.alwaysTrue() : CommandBase.convertArgToBlockStatePredicate(replaceBlock, args[4])) : Predicates.alwaysTrue();
					}
					tag = getNbtTagCompound(args, state, block, 5);
					break;
			}
		}

		int j = 0;
		for (BlockPos pos : BlockPos.getAllInBoxMutable(start, end)) {
			switch (category) {
				case 2:
					world.destroyBlock(pos, true);
					break;
				case 1:
					if (!world.isAirBlock(pos)) {
						continue;
					}
					break;
				case 0:
					if (replaceBlock != null) {
						IBlockState ss = world.getBlockState(pos);
						if (ss.getBlock() != replaceBlock || !replaceTo.test(ss)) {
							continue;
						}
					}
					break;
			}

			TileEntity tile = world.getTileEntity(pos);

			if (tile instanceof IInventory) {
				((IInventory) tile).clear();
			}
			world.removeTileEntity(pos);

			if (world.setBlockState(pos, state, BlockHelper.PLACEBLOCK_SENDCHANGE | BlockHelper.PLACEBLOCK_NO_OBSERVER)) {
				j++;

				if (tag != null) {
					TileEntity tile1 = world.getTileEntity(pos);

					if (tile1 != null) {
						tag.setInteger("x", pos.getX());
						tag.setInteger("y", pos.getY());
						tag.setInteger("z", pos.getZ());
						tile1.readFromNBT(tag);
					}
				}
			}
		}

		if (j <= 0) {
			notifyCommandListener(sender, "commands.fill.failed");
		} else {
			sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, j);
			notifyCommandListener(sender, "commands.fill.success", j);
		}
	}

	public static NBTTagCompound getNbtTagCompound(String[] args, IBlockState state, Block block, int index) throws CommandException {
		if (args.length > index && block.hasTileEntity(state)) {
			NBTTagCompound tag = new NBTTagCompound();

			String s = makePath(args, index);

			try {
				return JsonToNBT.getTagFromJson(s);
			} catch (NBTException e) {
				throw new CommandException("commands.fill.tagError", e.getMessage());
			}
		}
		return null;
	}

	@Override
	public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
		switch (args.length) {
			case 1:
				return CommandBase.getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
			case 3:
				return CommandBase.getListOfStringsMatchingLastWord(args, "replace", "destroy", "keep");
			case 4:
				return "replace".equals(args[1]) ? CommandBase.getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys()) : EMPTY;
		}
		return EMPTY;
	}
}
