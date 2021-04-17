package ilib.world.saver;

import ilib.ATHandler;
import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.util.DimensionHelper;
import ilib.util.PlayerUtil;
import roj.concurrent.task.ITask;
import roj.concurrent.timing.Scheduler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;

/**
 * @author Roj234
 * @since 2021/1/31 21:56
 */
public final class WorldSaver {
	static boolean enable = false;
	static int sid = 0;
	static boolean enter = false;

	public static boolean isClientServer() {
		return ClientProxy.mc.getIntegratedServer() != null;
	}

	static boolean inited;

	@SubscribeEvent
	public static void onWorldUnload(WorldEvent.Unload event) {
		if (!enable) return;
		World w = event.getWorld();
		if (!w.isRemote) return;
		if ((w instanceof WorldClient) && !isClientServer() && w.chunkProvider instanceof ChunkSavingProvider) {
			try {
				ChunkSavingProvider savingProvider = (ChunkSavingProvider) w.chunkProvider;
				savingProvider.saveWorld();
				Thread.sleep(200);
			} catch (Throwable e) {
				PlayerUtil.sendTo(null, "尾巴保存失败: " + e);
				e.printStackTrace();
			}
		}
	}

	@SubscribeEvent
	public static void onWorldLoad(WorldEvent.Load event) {
		if (!enable) return;
		onWorldLoad(event.getWorld());
	}

	public static void onWorldLoad(World w) {
		if (!w.isRemote) return;
		if ((w instanceof WorldClient) && !isClientServer()) {
			ImpLib.logger().debug("Injecting world " + w.provider.getDimension());
			// debug
			try {
				StringBuilder sb = new StringBuilder("saves/世界保存_").append(sid).append("/");
				int dim = DimensionHelper.idFor(w);
				if (dim != 0) {
					sb.append("DIM").append(dim).append("/");
				}
				File worldPath = new File(sb.toString());
				WriteOnlySaveHandler handler = new WriteOnlySaveHandler(worldPath, w.getWorldInfo());
				ChunkSavingProvider savingProvider = new ChunkSavingProvider(w, worldPath);
				ilib.ATHandler.setChunkProvider(w, savingProvider);
				ilib.ATHandler.setClientChunkProvider((WorldClient) w, savingProvider);
				ATHandler.setMapStorage(w, new MapStorage(handler));
				Scheduler.getDefaultScheduler().executeLater(new BlockIdSaver(handler, w.getWorldInfo()), 1000);
				enter = true;
			} catch (Throwable e) {
				PlayerUtil.sendTo(null, "世界注入失败: " + e);
				throw new RuntimeException("Failure during injecting ChunkSavingProvider", e);
			}
		}
	}

	private static void _changeState() {
		if (enable) {
			if (!inited) {
				inited = true;
				TagGetter.register();
			}
			WorldClient w = Minecraft.getMinecraft().world;
			if (w != null) onWorldLoad(w);
		}
	}

	public static boolean toggleEnable() {
		enable = !enable;
		_changeState();
		return enable;
	}

	public static void setEnable(boolean enable1) {
		enable = enable1;
		_changeState();
	}

	public static boolean isEnabled() {
		return enable;
	}

	public static void plusSid() {
		if (enter) {
			sid++;
			enter = false;
		}
	}

	public static class BlockIdSaver implements Runnable, ITask {
		public BlockIdSaver(WriteOnlySaveHandler handler, WorldInfo info) {
			this.h = handler;
			this.i = info;
		}

		private final WriteOnlySaveHandler h;
		private final WorldInfo i;

		public void run() {
			NBTTagCompound tag = new NBTTagCompound();
			net.minecraftforge.fml.common.FMLCommonHandler.instance().handleWorldDataSave(h, i, tag);
			h.saveData0(i, new NBTTagCompound(), tag);
			PlayerUtil.sendTo(null, "方块ID数据已经保存!");
		}

		@Override
		public void execute() {
			Minecraft.getMinecraft().addScheduledTask(this);
		}
	}

	static {
		MinecraftForge.EVENT_BUS.register(WorldSaver.class);
	}
}
