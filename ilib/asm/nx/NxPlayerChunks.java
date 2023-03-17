package ilib.asm.nx;

import com.google.common.base.Predicate;
import ilib.Config;
import ilib.asm.util.AsyncChunkGenerator;
import ilib.util.PlayerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import roj.RequireUpgrade;
import roj.asm.nixim.*;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author solo6975
 * @since 2022/5/22 0:16
 */
@Nixim("/")
class NxPlayerChunks extends PlayerChunkMap {
	@Shadow
	private static Predicate<EntityPlayerMP> NOT_SPECTATOR;
	@Shadow
	private static Predicate<EntityPlayerMP> CAN_GENERATE_CHUNKS;

	@Copy(staticInitializer = "abab")
	private static Comparator<PlayerChunkMapEntry> DIST;

	static void abab() {
		DIST = (o1, o2) -> Double.compare(o2.getClosestPlayerDistance(), o1.getClosestPlayerDistance());
	}

	@Shadow
	private WorldServer world;
	@Shadow
	private List<EntityPlayerMP> players;
	@Shadow
	private Long2ObjectMap<PlayerChunkMapEntry> entryMap;
	@Shadow
	private Set<PlayerChunkMapEntry> dirtyEntries;

	@Shadow
	private List<PlayerChunkMapEntry> pendingSendToPlayers;
	@Shadow
	private List<PlayerChunkMapEntry> entriesWithoutChunks;
	@Shadow
	private boolean sortMissingChunks = true;
	@Shadow
	private boolean sortSendToPlayers = true;

	@Shadow
	private List<PlayerChunkMapEntry> entries;
	@Shadow
	private int playerViewRadius;
	@Shadow
	private long previousTotalWorldTime;

	@Dynamic("optifine")
	@Copy(unique = true)
	private long timeMarkOF;

	public NxPlayerChunks() {
		super(null);
	}

	@Inject(value = "<init>", at = Inject.At.TAIL)
	public void aaa(WorldServer _lvt_1_) {
		$$$CONSTRUCTOR();
		pendingSendToPlayers = new SimpleList<>();
		entriesWithoutChunks = new SimpleList<>();
		dirtyEntries = new MyHashSet<>();
	}

	private void $$$CONSTRUCTOR() {}

	@Inject("/")
	public void tick() {
		long time = world.getTotalWorldTime();
		if (time - previousTotalWorldTime > 8000L) {
			previousTotalWorldTime = time;

			for (int i = 0; i < entries.size(); ++i) {
				PlayerChunkMapEntry entry = entries.get(i);
				entry.update();
				entry.updateChunkInhabitedTime();
			}
		}

		if (!dirtyEntries.isEmpty()) {
			for (PlayerChunkMapEntry entry : dirtyEntries) {
				entry.update();
			}
			dirtyEntries.clear();
		}

		if (sortMissingChunks && time % 4L == 0L) {
			sortMissingChunks = false;
			entriesWithoutChunks.sort(DIST);
		}

		if (sortSendToPlayers && time % 4L == 2L) {
			sortSendToPlayers = false;
			pendingSendToPlayers.sort(DIST);
		}

		if ((time & 1) != 0) {
			if (!entriesWithoutChunks.isEmpty()) {
				long timeout = System.nanoTime() + Config.maxChunkTimeTick;
				int v = Config.maxChunkTick;
				for (int i = 0; i < entriesWithoutChunks.size(); i++) {
					PlayerChunkMapEntry entry = entriesWithoutChunks.get(i);
					if (entry.getChunk() == null) {
						boolean canGenerate = entry.hasPlayerMatching(CAN_GENERATE_CHUNKS);
						if (entry.providePlayerChunk(canGenerate)) {
							entriesWithoutChunks.remove(i--);
							if (entry.sendToPlayers()) {
								pendingSendToPlayers.remove(entry);
							}

							if (--v == 0 || System.nanoTime() > timeout) break;
						}
					}
				}
				if (Config.asyncChunkGen) AsyncChunkGenerator.sync(timeout);
			}

			if (!pendingSendToPlayers.isEmpty()) {
				int v = Math.max(pendingSendToPlayers.size() - 81, 0);
				for (int i = pendingSendToPlayers.size() - 1; i >= v; i--) {
					PlayerChunkMapEntry entry = pendingSendToPlayers.get(i);
					if (entry.sendToPlayers()) pendingSendToPlayers.remove(i);
				}
			}
		}

		if (players.isEmpty()) {
			WorldProvider wp = world.provider;
			if (!wp.canRespawnHere()) {
				world.getChunkProvider().queueUnloadAll();
			}
		}
	}

	@Inject(value = "/", at = Inject.At.TAIL)
	public void removeEntry(PlayerChunkMapEntry entry) {
		NxAsyncPCME apm = (NxAsyncPCME) entry;
		if (apm.asyncGenerateTask != null) apm.asyncGenerateTask.cancel(true);
	}

	public void updateMovingPlayer(EntityPlayerMP player) {
		int x = (int)player.posX >> 4;
		int z = (int)player.posZ >> 4;

		double dx1 = player.managedPosX - player.posX;
		double dz1 = player.managedPosZ - player.posZ;
		if (dx1 * dx1 + dz1 * dz1 < 64.0) return;

		int prevX = (int)player.managedPosX >> 4;
		int prevZ = (int)player.managedPosZ >> 4;
		int r = playerViewRadius;
		int var_r2 = getPlayerViewRadiusBySpeed(player.motionX * player.motionX + player.motionZ * player.motionZ);

		int dx = x - prevX;
		int dz = z - prevZ;
		if (dx != 0 || dz != 0) {
			for(int x1 = x - r; x1 <= x + r; ++x1) {
				for(int z1 = z - r; z1 <= z + r; ++z1) {
					if (!overlaps(x1, z1, prevX, prevZ, r)) {
						/*int tmp1 = x1-x;
						int tmp2 = tmp1*tmp1;
						tmp1 = z1-z;
						tmp2 += tmp1*tmp1;

						if (tmp2 < var_r2)*/ getOrCreateEntry(x1, z1).addPlayer(player);
					}

					if (!overlaps(x1 - dx, z1 - dz, x, z, r)) {
						PlayerChunkMapEntry entry = getEntry(x1 - dx, z1 - dz);
						if (entry != null) entry.removePlayer(player);
					}
				}
			}

			player.managedPosX = player.posX;
			player.managedPosZ = player.posZ;
			markSortPending();
		}
	}

	@RequireUpgrade
	@Copy
	private int getPlayerViewRadiusBySpeed(double speed_2) {
		PlayerUtil.broadcastAll(String.valueOf(speed_2));
		return 0;//playerViewRadius * ();
	}

	@Inject("/")
	public void setPlayerViewRadius(int radius) {
		radius = MathHelper.clamp(radius, 3, 32);
		int delta = radius - playerViewRadius;
		if (delta != 0) {
			for (int i = 0; i < players.size(); i++) {
				EntityPlayerMP player = players.get(i);
				int px = (int) player.posX >> 4;
				int pz = (int) player.posZ >> 4;
				int x, z;
				if (delta > 0) {
					for (x = px - radius; x <= px + radius; ++x) {
						for (z = pz - radius; z <= pz + radius; ++z) {
							PlayerChunkMapEntry entry = getOrCreateEntry(x, z);
							if (!entry.containsPlayer(player)) entry.addPlayer(player);
						}
					}
				} else {
					for (x = px - playerViewRadius; x <= px + playerViewRadius; ++x) {
						for (z = pz - playerViewRadius; z <= pz + playerViewRadius; ++z) {
							if (!overlaps(x, z, px, pz, radius)) {
								PlayerChunkMapEntry entry = getEntry(x, z);
								if (entry != null) entry.removePlayer(player);
							}
						}
					}
				}
			}

			playerViewRadius = radius;
			markSortPending();
		}
	}

	@Shadow
	private PlayerChunkMapEntry getOrCreateEntry(int chunkX, int chunkZ) {
		return null;
	}

	@Shadow
	private static long getIndex(int chunkX, int chunkZ) {
		return 0;
	}

	@Shadow
	private boolean overlaps(int x1, int z1, int x2, int z2, int radius) {
		return false;
	}

	@Shadow
	private void markSortPending() {}
}