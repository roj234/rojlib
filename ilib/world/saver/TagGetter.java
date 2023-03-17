package ilib.world.saver;

import ilib.ClientProxy;
import ilib.world.saver.tag.AsyncPacket;
import ilib.world.saver.tag.ITagGetter;
import ilib.world.saver.tag.WailaTagGetter;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.task.AsyncTask;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/3/31 21:30
 */
public class TagGetter {
	private static ITagGetter delegate;
	private static long lastUpdate;
	private static final SimpleList<TileEntity> nearbyTiles = new SimpleList<>();
	private static final SimpleList<Entity> nearbyEntities = new SimpleList<>();
	private static final MyHashMap<Object, AsyncPacket> requesting = new MyHashMap<>();

	public static void register() {
		MinecraftForge.EVENT_BUS.register(TagGetter.class);
		WailaTagGetter.register();
	}

	@SubscribeEvent
	public static void onJoinServer(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		if (!WorldSaver.enable) return;

		WailaTagGetter.isWailaInstalled = false;
		delegate = null;

		WorldSaver.executor.register(AsyncTask.fromVoid(() -> {
			if (WailaTagGetter.isWailaInstalled) {
				delegate = new WailaTagGetter();
			}
		}), 1000, 1000, 1);
	}

	@SubscribeEvent
	public static void onTick(TickEvent.ClientTickEvent event) {
		if (!WorldSaver.enable || delegate == null || ClientProxy.mc.world == null || ClientProxy.mc.isIntegratedServerRunning()) return;

		checkReply();
		sendRequest();

		long t = System.currentTimeMillis();
		if (t - lastUpdate < 2000) return;
		lastUpdate = t;

		findNearby();
	}

	private static void findNearby() {
		nearbyTiles.clear();
		nearbyEntities.clear();

		EntityPlayerSP me = ClientProxy.mc.player;
		WorldClient at = ClientProxy.mc.world;
		if (delegate.supportTile()) {
			for (BlockPos.MutableBlockPos pos : BlockPos.MutableBlockPos.getAllInBoxMutable(me.getPosition().add(16, 16, 16), me.getPosition().add(-16, -16, -16))) {
				TileEntity tile = at.getTileEntity(pos);
				if (tile != null) {
					nearbyTiles.add(tile);
				}
			}
			System.err.println("正在追加附近的TE:" + nearbyTiles);
		}
		if (delegate.supportEntity()) {
			List<Entity> entities = at.getEntitiesWithinAABBExcludingEntity(me, new AxisAlignedBB(me.getPosition().add(16, 16, 16), me.getPosition().add(-16, -16, -16)));
			nearbyEntities.addAll(entities);
			System.err.println("正在追加附近的实体:" + nearbyEntities);
		}
	}

	private static void checkReply() {
		Iterator<Map.Entry<Object, AsyncPacket>> itr = requesting.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<Object, AsyncPacket> entry = itr.next();
			AsyncPacket future = entry.getValue();
			if (future.finished()) {
				NBTTagCompound tag;
				try {
					tag = future.get();
				} catch (Throwable e) {
					e.printStackTrace();
					continue;
				}
				Object o = entry.getKey();
				if (o instanceof TileEntity) {
					TileEntity te = (TileEntity) o;
					if (!te.isInvalid()) te.readFromNBT(tag);
				} else {
					Entity e = (Entity) o;
					if (e.isEntityAlive()) e.readFromNBT(tag);
				}
				itr.remove();
			}
		}
	}

	private static void sendRequest() {
		if (!delegate.isBusy()) {
			while (!nearbyTiles.isEmpty()) {
				TileEntity tile = nearbyTiles.remove(nearbyTiles.size() - 1);
				if (!tile.isInvalid()) {
					requesting.put(tile, delegate.getTag(tile));
				}
				if (delegate.isBusy()) return;
			}
			while (!nearbyEntities.isEmpty()) {
				Entity entity = nearbyEntities.remove(nearbyEntities.size() - 1);
				if (entity.isEntityAlive()) {
					requesting.put(entity, delegate.getTag(entity));
				}
				if (delegate.isBusy()) return;
			}
		}
	}
}
