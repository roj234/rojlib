package ilib.command.sub;

import ilib.ImpLib;
import ilib.math.Arena;
import ilib.math.Section;
import ilib.math.SelectionCache;
import ilib.world.schematic.SchematicLoader;
import ilib.world.structure.Structure;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class CmdSchematic extends AbstractSubCommand {
	@Override
	public String getName() {
		return "structure";
	}

	@Override
	public String getHelp() {
		return "command.il.help.structure";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		World w = sender.getEntityWorld();

		switch (args.length) {
			case 2:
			case 3: {
				EntityPlayer player = getAsPlayer(sender);
				boolean flag = args[0].equals("save");
				Arena arena = SelectionCache.get(player.getUniqueID().getMostSignificantBits());
				if (arena == null) {
					throw new CommandException("command.ilib.sel.arena_not");
				}
				if (arena.isOK()) {
					if (!flag) {
						throw new CommandException("command.ilib.sel.unknown_pos");
					}
					Section sect = arena.toSection();
					saveStructure(w, args[1], sect.minBlock(), sect.maxBlock());
				} else {
					if (flag) {
						throw new CommandException("command.ilib.sel.arena_not");
					}
					BlockPos pos0 = arena.getP1() == null ? arena.getP2() : arena.getP1();
					File file = new File("config/schematic/" + args[1]);
					if (!file.exists()) {
						throw new CommandException("command.ilib.404");
					}
					Structure structure = new Structure(SchematicLoader.load(file));

					int type = args.length < 3 ? Structure.F_REPLACE_AIR : Integer.parseInt(args[2]);

					structure.generate(w, pos0, type);
				}
			}
			break;
			default:
				throw new WrongUsageException("/il structure <save/load> <name> [mode]");
		}

		notifyCommandListener(sender, "command.ilib.ok");
	}

	private static void saveStructure(World w, String name, BlockPos pos0, BlockPos pos1) throws CommandException {
		BlockPos start = new BlockPos(Math.min(pos0.getX(), pos1.getX()), Math.min(pos0.getY(), pos1.getY()), Math.min(pos0.getZ(), pos1.getZ()));
		BlockPos len = new BlockPos(Math.abs(pos1.getX() - pos0.getX()) + 1, Math.abs(pos1.getY() - pos0.getY()) + 1, Math.abs(pos1.getZ() - pos0.getZ()) + 1);
		File saveDir = new File("config/schematic/");
		saveDir.mkdirs();

		try (FileOutputStream out = new FileOutputStream(new File(saveDir, name))) {
			CompressedStreamTools.writeCompressed(SchematicLoader.write(w, start, len, true), out);
		} catch (IOException e) {
			ImpLib.logger().warn("Error saving structure", e);
			throw new CommandException(e.toString());
		}
	}
}
