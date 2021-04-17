package ilib.command.sub;

import ilib.Config;
import ilib.ImpLib;
import ilib.client.TextureHelper;
import ilib.misc.MCHooks;
import ilib.misc.ps.Cheat;
import ilib.util.ChatColor;
import ilib.util.DimensionHelper;
import ilib.util.ItemUtils;
import ilib.util.PlayerUtil;
import ilib.util.internal.TileasBe;
import roj.collect.LongMap;
import roj.collect.MyHashSet;
import roj.opengl.util.OpenGLDebug;
import roj.text.TextUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;

import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/5/23 14:10
 */
// todo 纯客户端的schematic saving
public abstract class MySubs extends AbstractSubCommand {
	static LongMap<Object> generatedChunks;

	public static final MySubs REGEN = new MySubs("regenLoadedChunks") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (generatedChunks == null) generatedChunks = new LongMap<>();

			World world = sender.getEntityWorld();

			int i = 0;

			final ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
			final IChunkGenerator generator = provider.chunkGenerator;
			for (Chunk chunk : provider.getLoadedChunks()) {
				if (generatedChunks.put(((long) chunk.x << 32) | (long) chunk.z, true) == Boolean.TRUE) continue;
				generator.populate(chunk.x, chunk.z);
				GameRegistry.generateWorld(chunk.x, chunk.z, world, generator, provider);
				chunk.markDirty();
				i++;
			}
			PlayerUtil.sendTo(sender, ChatColor.GREY + "成功重生了 " + ChatColor.ORANGE + i + ChatColor.GREY + " 个区块");
		}
	};

	public static final MySubs GC = new MySubs("gc") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			Runtime r = Runtime.getRuntime();
			long before = r.totalMemory() - r.freeMemory();
			System.gc();
			long after = r.totalMemory() - r.freeMemory();
			PlayerUtil.sendTo(sender, ChatColor.GREY + "成功释放了 " + ChatColor.ORANGE + TextUtil.scaledNumber(before - after) + 'B' + ChatColor.GREY + " 内存");
		}
	};

	public static final MySubs TPS_CHANGE = new MySubs("tps") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (!Config.enableTPSChange) {
				throw new WrongUsageException("你需要在配置文件中启用它.");
			}
			if (args.length == 0) throw new WrongUsageException("/il tps <tps>");
			if (TextUtil.isNumber(args[0]) != 0) throw new WrongUsageException("/il tps <tps>");
			int i = Integer.parseInt(args[0]);
			if (i < 0 || i > 1000) {
				throw new WrongUsageException("Tps 必须在1-1000或者为0(不限制)");
			}
			MCHooks.MSpT = i != 0 ? 1000 / i : 0;
			PlayerUtil.sendTo(sender, ChatColor.GREY + "已将TPS设为 " + ChatColor.ORANGE + args[0]);
		}
	};

	public static final MySubs UNLOAD_CHUNKS = new MySubs("chunks") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length < 1) {
				throw new WrongUsageException("/il chunks <view/clear>");
			}
			switch (args[0]) {
				case "clear": {
					int i = 0;
					Set<String> set = new MyHashSet<>();
					for (WorldServer w : DimensionManager.getWorlds()) {
						if (w.playerEntities.isEmpty()) {
							DimensionManager.unloadWorld(DimensionHelper.idFor(w));
							set.add(String.valueOf(DimensionHelper.idFor(w)));
						} else if (w.getChunkProvider() != null) {
							ChunkProviderServer provider = w.getChunkProvider();

							PlayerChunkMap map = w.getPlayerChunkMap();
							LongMap<Boolean> longs = new LongMap<>();
							for (Iterator<Chunk> itr = map.getChunkIterator(); itr.hasNext(); ) {
								Chunk chunk = itr.next();
								longs.putLong((long) chunk.x << 32 | (long) chunk.z, null);
							}

							for (Chunk chunk : provider.getLoadedChunks()) {
								if (!longs.containsKey((long) chunk.x << 32 | (long) chunk.z)) {
									provider.queueUnload(chunk);
									i++;
								}
							}
						} else {
							PlayerUtil.sendTo(sender, ChatColor.GREY + "维度ID " + ChatColor.ORANGE + DimensionHelper.idFor(w) + ChatColor.GREY + " 不支持卸载");
						}
					}
					PlayerUtil.sendTo(sender, ChatColor.GREY + "成功卸载 " + ChatColor.ORANGE + i + ChatColor.GREY + " 个区块");
					if (!set.isEmpty()) PlayerUtil.sendTo(sender, ChatColor.GREY + "成功卸载 " + ChatColor.ORANGE + set + ChatColor.GREY + " 世界");
				}
				break;
				case "view": {
					for (WorldServer w : DimensionManager.getWorlds()) {
						String s = "未知";
						if (w.getChunkProvider() != null) {
							s = String.valueOf(w.getChunkProvider().getLoadedChunks().size());
						}
						PlayerUtil.sendTo(sender, ChatColor.GREY + "维度ID " + ChatColor.ORANGE + DimensionHelper.idFor(w) + ChatColor.GREY + " 加载了 " + ChatColor.ORANGE + s + ChatColor.GREY + " 个区块");
					}
				}
				break;
				default:
			}
		}
	};

	public static final MySubs CHECK_OD = new MySubs("itemod") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length < 1) {
				throw new WrongUsageException("/il itemod <oredict>");
			}

			List<ItemStack> stacks = OreDictionary.getOres(args[0], false);
			sender.sendMessage(new TextComponentString("\u00a7a  矿物词典 " + args[0] + " 查询到的物品(" + stacks.size() + ')'));
			for (ItemStack stack : stacks) {
				sender.sendMessage(new TextComponentString("\u00a77 - " + stack.getItem().getItemStackDisplayName(stack) + "(" + ItemUtils.stack2String(stack) + ")"));
			}
		}
	};
	public static final MySubs RENDER_INFO = new MySubs("info") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			TileasBe.onRender();
		}
	};
	public static final MySubs TILE_TEST = new MySubs("test") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length == 0) {ilib.util.internal.T3SpecialRenderer.release();} else {
				int index = args[0].indexOf('B') - 1;
				if (index < 0) return;
				String b = args[0].substring(index);
				String i = args[0].substring(0, index - 1);
				ilib.util.internal.T3SpecialRenderer.render(b, i);
			}
		}
	};
	public static final MySubs BLOCK_UPDATE = new MySubs("update") {
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (args.length < 1) {
				sender.sendMessage(new TextComponentString("/il update <radius>"));
				return;
			}

			int r = Integer.parseInt(args[0]);
			World world = sender.getEntityWorld();

			int counter = 0;
			for (BlockPos pos : BlockPos.getAllInBoxMutable(sender.getPosition().add(-r, -r, -r), sender.getPosition().add(r, r, r))) {
				IBlockState state = world.getBlockState(pos);

				if (state.getBlock() != Blocks.AIR) {
					world.notifyBlockUpdate(pos, state, state, 4);
					world.updateBlockTick(pos, state.getBlock(), 0, 1);
					counter++;
				}
			}

			sender.sendMessage(new TextComponentString(Integer.toString(counter)));
		}
	};

	public static MySubs DUMP_GL_INFO, RELOAD_TEXTURE, PACKET_SIMULATOR;

	static {
		if (ImpLib.isClient) clientInit();
	}

	@SideOnly(Side.CLIENT)
	public static void clientInit() {
		DUMP_GL_INFO = new MySubs("dump_gl_info") {
			public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
				for (String s : OpenGLDebug.dumpStrings())
					PlayerUtil.sendTo(null, s);
			}
		};

		RELOAD_TEXTURE = new MySubs("reloadTex") {
			public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
				TextureHelper.manualReload();
				sender.sendMessage(new TextComponentString("材质已经重新加载."));
			}
		};

		if ((Config.debug & 128) == 0) return;

		PACKET_SIMULATOR = Cheat.getPrimaryCommand();
	}

	private final String name, help;

	public MySubs(String name) {
		this.name = name;
		this.help = null;
	}

	public MySubs(String name, String help) {
		this.name = name;
		this.help = help;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getHelp() {
		return this.help == null ? super.getHelp() : this.help;
	}

	@Override
	public abstract void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;
}
