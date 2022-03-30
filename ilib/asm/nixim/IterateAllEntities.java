package ilib.asm.nixim;

import ilib.ImpLib;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

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

/**
 * @author solo6975
 * @since 2022/4/6 21:13
 */
@Nixim("net.minecraft.entity.EntityTracker")
class IterateAllEntities extends EntityTracker {
    @Shadow("field_72794_c")
    IntHashMap<EntityTrackerEntry> trackedEntityHashTable;

    public IterateAllEntities(WorldServer theWorldIn) {
        super(theWorldIn);
    }

    @Inject("func_85172_a")
    public void sendLeashedEntitiesInChunk(EntityPlayerMP player, Chunk chunkIn) {
        IntHashMap<EntityTrackerEntry> ht = this.trackedEntityHashTable;
        NetHandlerPlayServer conn = player.connection;

        for (ClassInheritanceMultiMap<Entity> map : chunkIn.getEntityLists()) {
            for (Entity entity : map) {
                EntityTrackerEntry entry = ht.lookup(entity.getEntityId());
                if (entry == null) {
                    ImpLib.logger().warn("无效的实体ID " + entity.getClass().getName() + ": " + entity.getEntityId() + " | " + entity);
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
