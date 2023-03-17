package ilib.asm.nx;

import ilib.ImpLib;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.AbstractIterator;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.SPacketEntityAttach;
import net.minecraft.network.play.server.SPacketSetPassengers;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.IntHashMap;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.Set;

/**
 * @author solo6975
 * @since 2022/4/6 21:13
 */
@Nixim("/")
class IterateAllEntities extends EntityTracker {
	@Shadow
	Set<EntityTrackerEntry> entries;
	@Shadow("field_72794_c")
	IntHashMap<EntityTrackerEntry> trackedEntityHashTable;
	@Shadow
	WorldServer world;

	@Copy(unique = true)
	AbstractIterator<EntityTrackerEntry> entriesItr;
	@Copy(unique = true, staticInitializer = "cra")
	static SimpleList<EntityPlayerMP> playerTmp;

	static void cra() {
		playerTmp = new SimpleList<>();
	}

	@Inject(value = "/", at = Inject.At.TAIL)
	IterateAllEntities(WorldServer theWorldIn) {
		super(theWorldIn);
		MyHashSet<EntityTrackerEntry> set = new MyHashSet<>();
		entries = set;
		entriesItr = set.setItr();
	}

	@Inject("/")
	public void untrack(Entity entity) {
		if (entity instanceof EntityPlayerMP) {
			EntityPlayerMP player = (EntityPlayerMP) entity;

			AbstractIterator<EntityTrackerEntry> itr = entriesItr;
			itr.reset();

			while (itr.hasNext()) {
				itr.next().removeFromTrackedPlayers(player);
			}
		}

		EntityTrackerEntry entry = trackedEntityHashTable.removeObject(entity.getEntityId());
		if (entry != null) {
			entries.remove(entry);
			entry.sendDestroyEntityPacketToTrackedPlayers();
		}
	}

	@Inject("/")
	public void tick() {
		SimpleList<EntityPlayerMP> list = playerTmp;
		playerTmp.clear();

		AbstractIterator<EntityTrackerEntry> itr = entriesItr;
		itr.reset();

		while (itr.hasNext()) {
			EntityTrackerEntry entry = itr.next();
			entry.updatePlayerList(world.playerEntities);
			if (entry.playerEntitiesUpdated) {
				Entity entity = entry.getTrackedEntity();
				if (entity instanceof EntityPlayerMP) {
					list.add((EntityPlayerMP) entity);
				}
			}
		}

		itr.reset();
		while (itr.hasNext()) {
			EntityTrackerEntry entry = itr.next();
			for (int i = 0; i < list.size(); i++) {
				EntityPlayerMP player = list.get(i);
				if (entry.getTrackedEntity() != player) {
					entry.updatePlayerEntity(player);
				}
			}
		}
	}

	@Inject("/")
	public void updateVisibility(EntityPlayerMP player) {
		AbstractIterator<EntityTrackerEntry> itr = entriesItr;
		itr.reset();

		while (itr.hasNext()) {
			EntityTrackerEntry entry = itr.next();
			if (entry.getTrackedEntity() == player) {
				entry.updatePlayerEntities(world.playerEntities);
			} else {
				entry.updatePlayerEntity(player);
			}
		}

	}

	@Inject("/")
	public void removePlayerFromTrackers(EntityPlayerMP player) {
		AbstractIterator<EntityTrackerEntry> itr = entriesItr;
		itr.reset();

		while (itr.hasNext()) {
			itr.next().removeTrackedPlayerSymmetric(player);
		}
	}

	@Inject("/")
	public void sendLeashedEntitiesInChunk(EntityPlayerMP player, Chunk chunkIn) {
		IntHashMap<EntityTrackerEntry> ht = this.trackedEntityHashTable;
		NetHandlerPlayServer conn = player.connection;

		for (ClassInheritanceMultiMap<Entity> map : chunkIn.getEntityLists()) {
			for (Entity entity : map) {
				EntityTrackerEntry entry = ht.lookup(entity.getEntityId());
				if (entry == null) {
					if (entity != player) ImpLib.logger().warn("无效的实体ID " + entity.getClass().getName() + ": " + entity.getEntityId() + " | " + entity);
					continue;
				}
				entry.updatePlayerEntity(player);
				if (entity instanceof EntityLiving) {
					EntityLiving lv = (EntityLiving) entity;
					if (lv.getLeashHolder() != null) {
						conn.sendPacket(new SPacketEntityAttach(entity, lv.getLeashHolder()));
					}
				}

				if (!entity.getPassengers().isEmpty()) {
					conn.sendPacket(new SPacketSetPassengers(entity));
				}
			}
		}
	}
}
