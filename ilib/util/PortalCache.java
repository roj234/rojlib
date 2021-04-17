package ilib.util;

import roj.collect.LongMap;
import roj.collect.MyHashSet;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Iterator;
import java.util.Set;

public final class PortalCache {
	static class Entry extends MyHashSet<BlockPos> {
		long active;
	}

	private final LongMap<Entry> map = new LongMap<>();

	public static final PortalCache OVERWORLD_CACHE = new PortalCache();
	public static final PortalCache NETHER_CACHE = new PortalCache();

	static {
		MinecraftForge.EVENT_BUS.register(PortalCache.class);
	}

	@SubscribeEvent
	public static void onPortalBlockEvent(BlockEvent e) {
		final BlockPos pos = e.getPos();
		final World world = e.getWorld();
		if (!world.isRemote && pos.getY() < world.provider.getActualHeight()) {
			PortalCache handler;
			switch (DimensionHelper.idFor(world)) {
				case -1:
					handler = NETHER_CACHE;
					break;
				case 0:
					handler = OVERWORLD_CACHE;
					break;
				default:
					return;
			}
			Set<BlockPos> set = handler.contains(e.getPos().getX() >> 4, e.getPos().getZ() >> 4);
			if (set != null) {
				if (e.getState().getBlock() == Blocks.PORTAL) {
					set.add(pos);
				} else {
					set.remove(pos);
				}
			}
		}
	}

	public static void removeStalePortalLocations(long worldTime) {
		if (worldTime % 1200L == 0L) { // every minute
			// clear 5 minutes before
			long time = System.currentTimeMillis() - 300000;
			for (Iterator<Entry> itr = NETHER_CACHE.map.values().iterator(); itr.hasNext(); ) {
				Entry entry = itr.next();
				if (entry.active - time < 0) {
					itr.remove();
				}
			}
			for (Iterator<Entry> itr = OVERWORLD_CACHE.map.values().iterator(); itr.hasNext(); ) {
				Entry entry = itr.next();
				if (entry.active - time < 0) {
					itr.remove();
				}
			}
		}
	}

	public Set<BlockPos> contains(int cx, int cz) {
		long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
		return map.get(key);
	}

	public Set<BlockPos> computeIfAbsent(int cx, int cz) {
		long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
		Entry pos = map.get(key);
		if (pos == null) {
			map.putLong(key, pos = new Entry());
			pos.add(null);
		}
		pos.active = System.currentTimeMillis();
		return pos;
	}

	public void clear() {
		map.clear();
	}
}
