package ilib.client.misc;

import ilib.ClientProxy;
import ilib.Config;
import ilib.util.DimensionHelper;
import ilib.util.EntityHelper;
import ilib.util.PlayerUtil;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import roj.reflect.DirectAccessor;
import roj.text.TextUtil;
import roj.util.Helpers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.GameSettings.Options;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.INpc;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author Roj233
 * @since 2021/10/20 0:57
 */
public class MyDebugOverlay {
	static MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

	interface Helper {
		int getDebugFPS();

		PriorityBlockingQueue<ChunkCompileTaskGenerator> getUpdate(ChunkRenderDispatcher dispatcher);

		BlockingQueue<RegionRenderCacheBuilder> getFree(ChunkRenderDispatcher dispatcher);

		Queue<?> getUpload(ChunkRenderDispatcher dispatcher);

		List<Thread> getWorkers(ChunkRenderDispatcher dispatcher);

		SoundManager getSoundManager(SoundHandler handler);

		Map<String, ISound> getPlaying(SoundManager sm);
	}

	static Helper h = DirectAccessor.builder(Helper.class)
									.access(Minecraft.class, "field_71470_ab", "getDebugFPS", null)
									.access(ChunkRenderDispatcher.class, new String[] {"field_178519_d", "field_178520_e", "field_178524_h", "field_178522_c"},
											new String[] {"getUpdate", "getFree", "getUpload", "getWorkers"}, null)
									.access(SoundHandler.class, "field_147694_f", "getSoundManager", null)
									.access(SoundManager.class, "field_148629_h", "getPlaying", null)
									.build();


	private static long time, tick;

	@SubscribeEvent
	public static void onTick(TickEvent.ServerTickEvent e) {
		if (time > 0 && e.phase == TickEvent.Phase.START) tick++;
	}

	static {
		MinecraftForge.EVENT_BUS.register(MyDebugOverlay.class);
	}

	public static void process(ArrayList<String> left, ArrayList<String> right) {
		left.clear();
		right.clear();

		Minecraft mc = ClientProxy.mc;
		GameSettings set = mc.gameSettings;

		left.ensureCapacity(20);
		left.add("\u00a76水雷艇 1.12.2 \u00a7r(" + mc.getVersion() + "/\u00a7b锻锤加护！\u00a7r)");
		left.add(
			"\u00a7f" + h.getDebugFPS() + "/" + (set.limitFramerate == Options.FRAMERATE_LIMIT.getValueMax() ? "\u00a7c无限制" : set.limitFramerate) + "\u00a7eFPS " + "\u00a7f" + RenderChunk.renderChunksUpdated + "\u00a7e区块更新" + (set.enableVsync ? " \u00a7a垂直同步" : "") + (set.fancyGraphics ? "" : " \u00a7b流畅") + (set.clouds == 1 ? " \u00a7c流畅的云" : "") + (OpenGlHelper.useVbo() ? " \u00a7dVBO" : ""));
		IntegratedServer s = Config.F3_ins ? (IntegratedServer) PlayerUtil.getMinecraftServer() : null;

		String tpsStr;
		if (s != null) {
			double MSPT = MathHelper.average(s.tickTimeArray) / 1000_000;
			tpsStr = " \u00a76TPS: " + (MSPT <= 50 ? "\u00a7a20(*)" : "\u00a7b" + (1000 / MSPT));
		} else {tpsStr = "";}

		left.add("\u00a76渲染距离: \u00a7a" + set.renderDistanceChunks + tpsStr);
		left.add("");

		EntityPlayerMP serverMe = s == null ? null : s.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
		Entity me = mc.getRenderViewEntity();
		left.add("\u00a7cX\u00a7aY\u00a7bZ: \u00a7c" + TextUtil.toFixed(me.posX, 4) + " \u00a7a" + TextUtil.toFixed(me.posY, 4) + " \u00a7b" + TextUtil.toFixed(me.posZ, 4));
		int intX, intY, intZ;
		left.add("\u00a7e方块: \u00a7c" + (intX = MathHelper.floor(me.posX)) + " \u00a7a" + (intY = MathHelper.floor(me.posY)) + " \u00a7b" + (intZ = MathHelper.floor(me.posZ)));
		left.add("\u00a7e区块: \u00a7c" + (intX >> 4) + " \u00a7a" + (intY >> 4) + " \u00a7b" + (intZ >> 4) + " \u00a7e相对 \u00a7c" + (intX & 15) + " \u00a7a" + (intY & 15) + " \u00a7b" + (intZ & 15));
		left.add("");

		if (Config.F3_render) {
			RenderGlobal rg = mc.renderGlobal;
			if (rg.renderDispatcher == null) {
				left.add("\u00a74渲染分派被模组修改或不可用");
			} else {
				left.add("\u00a7c渲染  \u00a7e正渲\u00a7f/\u00a7e等渲\u00a7f/\u00a7e等传\u00a7f/\u00a7e空闲");
				left.add("\u00a7c状态  \u00a7e" + getRenderedChunks(rg) + "\u00a7f/\u00a7e" + h.getUpdate(rg.renderDispatcher).size() + "\u00a7f/\u00a7e" + h.getUpload(rg.renderDispatcher)
																																						   .size() + "\u00a7f/\u00a7e" + h.getFree(
					rg.renderDispatcher).size() + (h.getWorkers(rg.renderDispatcher).isEmpty() ? " \u00a7c单线程" : ""));
			}
			left.add(s == null ? "" : "\u00a7c服务器加载区块: \u00a7e" + ((WorldServer) serverMe.world).getChunkProvider().loadedChunks.size());
			left.add(
				"\u00a7e光: \u00a7a" + rg.setLightUpdates.size() + " \u00a7eGTESR: \u00a7a" + rg.setTileEntities.size() + " \u00a7e实体: \u00a7a" + rg.countEntitiesHidden + "/" + rg.countEntitiesRendered + "/" + rg.countEntitiesTotal);
			left.add("");
		}

		EnumFacing face = me.getHorizontalFacing();
		left.add("\u00a7d位置: \u00a7f" + DimensionType.getById(DimensionHelper.idFor(me.world)).getName() + "/\u00a7e" + DimensionHelper.idFor(
			me.world) + "\u00a7f  " + face.getName() + "/\u00a7a" + (face.getAxisDirection().getOffset() > 0 ? "正" : "负") + face.getAxis().name());
		left.add("\u00a7d旋转: \u00a7f" + me.rotationYaw + " \u00a7d俯仰: \u00a7f" + me.rotationPitch);

		if (intY < 0 || intY > 255) {
			left.add("\u00a7d世界之外");
		} else {
			World w = mc.world;
			Chunk IamAt = w.getChunk(intX >> 4, intZ >> 4);
			if (IamAt.isEmpty()) {
				left.add("\u00a7d等待数据");
				left.add("");
				left.add("");
			} else {
				ExtendedBlockStorage storage = IamAt.getBlockStorageArray()[intY >> 4];
				if (storage != Chunk.NULL_BLOCK_STORAGE) {
					left.add("\u00a7c亮度: \u00a7e方块 \u00a7f" + storage.getBlockLight(intX & 15, intY & 15, intZ & 15) + (storage.getSkyLight() == null ? "" : " \u00a7e天空 \u00a7f" + storage.getSkyLight(
						intX & 15, intY & 15, intZ & 15)));
					BlockPos pos = mc.player.getPosition();
					left.add("\u00a7c群系: \u00a7e" + w.getBiome(pos).getRegistryName());

					DifficultyInstance difficulty = w.getDifficultyForLocation(pos);
					left.add("\u00a7c难度: \u00a7e" + difficulty.getAdditionalDifficulty() + " \u00a7f约化: \u00a7e" + difficulty.getClampedAdditionalDifficulty());
					if (mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null) {
						TileEntity tile = w.getTileEntity(mc.objectMouseOver.getBlockPos());
						left.add(tile == null ? "null" : tile.writeToNBT(new NBTTagCompound()).toString());
					}
				} else {
					left.add("\u00a7d空分块");
				}
			}

			if (serverMe != null) {
				w = serverMe.world;

				Chunk serverAt = w.getChunk(intX >> 4, intZ >> 4);
				ExtendedBlockStorage storage = serverAt.getBlockStorageArray()[intY >> 4];
				if (storage != Chunk.NULL_BLOCK_STORAGE) {
					left.add("\u00a7c亮度: \u00a7e方块 \u00a7f" + storage.getBlockLight(intX & 15, intY & 15, intZ & 15) + (storage.getSkyLight() == null ? "" : " \u00a7e天空 \u00a7f" + storage.getSkyLight(
						intX & 15, intY & 15, intZ & 15)));
					BlockPos pos = serverMe.getPosition();
					left.add("\u00a7c群系: \u00a7e" + w.getBiome(pos).getRegistryName());

					DifficultyInstance difficulty = w.getDifficultyForLocation(pos);
					left.add("\u00a7c难度: \u00a7e" + difficulty.getAdditionalDifficulty() + " \u00a7f约化: \u00a7e" + difficulty.getClampedAdditionalDifficulty());
				} else {
					left.add("\u00a7c空分块");
				}
			}
		}

		left.add("");
		left.add("\u00a7e扩展信息: 耗时饼图 [F3+shift]: " + (set.showDebugProfilerChart ? "\u00a7a开" : "\u00a7c关") + " TPS [F3+alt]: " + (set.showLagometer ? "\u00a7a开" : "\u00a7c关"));
		left.add("\u00a7e更多信息: \u00a7b[F3 + Q]");

		right.ensureCapacity(20);
		right.add("\u00a76 " + (mc.isJava64bit() ? "64" : "32") + "位" + System.getProperty("java.version"));

		long max = Runtime.getRuntime().maxMemory();
		long total = Runtime.getRuntime().totalMemory();
		long used = total - Runtime.getRuntime().freeMemory();
		right.add("\u00a76内存: \u00a7f" + (used * 100L / max) + "% " + (used >> 20) + "/" + (max >> 20) + "MB");
		right.add("\u00a76分配: \u00a7f" + (total * 100L / max) + "% " + (total >> 20) + "MB");

		MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
		max = nonHeap.getMax();
		if (max < 0) max = Runtime.getRuntime().maxMemory();
		used = nonHeap.getUsed();
		right.add("\u00a76直接内存: \u00a7f" + (used * 100L / max) + "% " + (used >> 20) + "/" + (max >> 20) + "MB");
		right.add("");
		if (Config.F3_hard) {
			right.add("\u00a76CPU: \u00a7f" + OpenGlHelper.getCpu());
			right.add("\u00a76显示: \u00a7f" + Display.getWidth() + "x" + Display.getHeight());
			right.add("\u00a76显卡: \u00a7f" + GL11.glGetString(GL11.GL_RENDERER));
			right.add("\u00a76OpenGL\u00a7f" + GL11.glGetString(GL11.GL_VERSION));
			right.add("");
		}

		if (serverMe != null) {
			if (time == 0) time = System.currentTimeMillis();
			long t = System.currentTimeMillis();
			if (t > time) {
				right.add("\u00a7a统计tps " + (tick / (float) (t - time) * 1000f));
				if (t - time > 5000) {
					time = t;
					tick = 0;
				}
			}
		}

		right.add(Loader.instance().getMCPVersionString());
		right.add("锻锤 " + ForgeVersion.getVersion());

		IFMLSidedHandler hdr = FMLCommonHandler.instance().getSidedDelegate();
		if (hdr != null) {
			right.addAll(hdr.getAdditionalBrandingInformation());
		}

		if (Loader.instance().getFMLBrandingProperties().containsKey("fmlbranding")) {
			right.add(Loader.instance().getFMLBrandingProperties().get("fmlbranding"));
		}

		int tModCount = Loader.instance().getModList().size();
		int aModCount = Loader.instance().getActiveModList().size();
		right.add(aModCount + "/" + tModCount + " 国防部们");
		right.add("");

		right.add("\u00a7e粒子/实体/怪物/动物/村民/玩家/悬挂/无生命");
		right.add(mc.effectRenderer.getStatistics() + "/" + mc.world.getLoadedEntityList().size() + filterData(mc.world.getLoadedEntityList()));
		if (s != null) right.add("\u00a7c-/" + serverMe.world.getLoadedEntityList().size() + filterData(serverMe.world.getLoadedEntityList()));
		right.add("");

		right.add("\u00a7d游戏时长: \u00a7b" + (mc.world.getTotalWorldTime() / 24000L) + "mch");
		if (mc.entityRenderer != null && mc.entityRenderer.isShaderActive()) {
			right.add("\u00a75光影: " + mc.entityRenderer.getShaderGroup().getShaderGroupName());
		}

		RayTraceResult rsl = mc.objectMouseOver;
		if (rsl != null && rsl.typeOfHit == Type.BLOCK && rsl.getBlockPos() != null) {
			BlockPos pos = rsl.getBlockPos();
			IBlockState state = mc.world.getBlockState(pos);
			if (mc.world.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
				state = state.getActualState(mc.world, pos);
			}

			right.add("");
			right.add("\u00a7e方块: \u00a7c" + pos.getX() + " \u00a7a" + pos.getY() + " \u00a7b" + pos.getZ());
			right.add(String.valueOf(Block.REGISTRY.getNameForObject(state.getBlock())));
			dumpState(right, state);
		}

		Vec3d start = new Vec3d(me.posX, me.posY + me.getEyeHeight(), me.posZ);
		Vec3d look = me.getLookVec();
		rsl = EntityHelper.rayTraceBlock(mc.world, EntityHelper.vec(start), EntityHelper.vec(start.add(look.scale(6))), EntityHelper.RT_STOP_ON_LIQUID);

		x:
		if (rsl != null && rsl.typeOfHit == Type.BLOCK && rsl.getBlockPos() != null) {
			BlockPos pos = rsl.getBlockPos();
			IBlockState state = mc.world.getBlockState(pos);
			if (mc.world.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
				state = state.getActualState(mc.world, pos);
			}

			if (!(state.getBlock() instanceof IFluidBlock) && !(state.getBlock() instanceof BlockLiquid)) {
				break x;
			}

			right.add("");
			right.add("\u00a7e液体: \u00a7c" + pos.getX() + " \u00a7a" + pos.getY() + " \u00a7b" + pos.getZ());
			right.add(String.valueOf(Block.REGISTRY.getNameForObject(state.getBlock())));
			dumpState(right, state);
		}

		SoundManager sm = h.getSoundManager(mc.getSoundHandler());
		Map<String, ISound> playingSounds = h.getPlaying(sm);
		right.add("");
		right.add("\u00a7e声音: " + playingSounds.size());
	}

	private static void dumpState(ArrayList<String> right, IBlockState state) {
		for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
			Object t = entry.getValue();
			String n1;
			if (Boolean.TRUE.equals(t)) {
				n1 = TextFormatting.GREEN + "true";
			} else if (Boolean.FALSE.equals(t)) {
				n1 = TextFormatting.RED + "false";
			} else {
				n1 = "\u00a7e" + entry.getKey().getName(Helpers.cast(t));
			}
			right.add("\u00a77" + entry.getKey().getName() + ": " + n1);
		}
	}

	private static String filterData(List<Entity> list) {
		int mst = 0, anm = 0, vil = 0, ply = 0, nlf = 0, pnt = 0;
		for (int i = 0; i < list.size(); i++) {
			Entity e = list.get(i);
			if (e instanceof EntityPlayer) {
				ply++;
			} else if (e instanceof IMob) {
				mst++;
			} else if (e instanceof INpc) {
				vil++;
			} else if (e instanceof IAnimals) {
				anm++;
			} else if (e instanceof EntityHanging) {
				pnt++;
			} else if (!(e instanceof EntityLivingBase)) {
				nlf++;
			}
		}
		return "/" + mst + "/" + anm + "/" + vil + "/" + ply + "/" + pnt + "/" + nlf;
	}

	static boolean ok = true;

	protected static int getRenderedChunks(RenderGlobal RG) {
		if (!ok) return -1;
		try {
			int i = 0;
			List<RenderGlobal.ContainerLocalRenderInformation> infos = RG.renderInfos;
			for (int j = 0; j < infos.size(); j++) {
				RenderGlobal.ContainerLocalRenderInformation info = infos.get(j);
				CompiledChunk cc = info.renderChunk.compiledChunk;
				if (cc != CompiledChunk.DUMMY && !cc.isEmpty()) i++;
			}
			return i;
		} catch (Error e) {
			ok = false;
			return -1;
		}
	}
}
