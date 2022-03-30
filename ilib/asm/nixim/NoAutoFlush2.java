package ilib.asm.nixim;

import ilib.asm.util.IFlushable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/6 21:13
 */
@Nixim(value = "net.minecraft.server.management.PlayerChunkMapEntry")
class NoAutoFlush2 extends PlayerChunkMapEntry {
    @Shadow("field_187282_b")
    private PlayerChunkMap playerChunkMap;
    @Shadow("field_187283_c")
    private List<EntityPlayerMP> players;
    @Shadow("field_187283_c")
    private Chunk chunk;
    @Shadow("field_187287_g")
    private int changes;
    @Shadow("field_187288_h")
    private int changedSectionFilter;
    @Shadow("field_187290_j")
    private boolean sentToPlayers;

    public NoAutoFlush2(PlayerChunkMap mapIn, int chunkX, int chunkZ) {
        super(mapIn, chunkX, chunkZ);
    }

    @Override
    @Inject("func_187272_b")
    public boolean sendToPlayers() {
        if (sentToPlayers) {
            return true;
        } else if (chunk == null) {
            return false;
        } else if (!chunk.isPopulated()) {
            return false;
        } else {
            changes = 0;
            changedSectionFilter = 0;
            sentToPlayers = true;
            if (!players.isEmpty()) {
                Packet<?> packet = new SPacketChunkData(chunk, 65535);

                for (int i = 0; i < players.size(); i++) {
                    EntityPlayerMP player = players.get(i);
                    IFlushable flushable = (IFlushable) player.connection.netManager;
                    flushable.setAutoFlush(false);
                    player.connection.sendPacket(packet);
                    playerChunkMap.getWorldServer()
                                       .getEntityTracker()
                                       .sendLeashedEntitiesInChunk(player, chunk);
                    MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(chunk, player));
                    flushable.setAutoFlush(true);
                }

            }
            return true;
        }
    }

    @Override
    @Inject("func_187278_c")
    public void sendToPlayer(EntityPlayerMP player) {
        if (sentToPlayers) {
            IFlushable flushable = (IFlushable) player.connection.netManager;
            flushable.setAutoFlush(false);
            player.connection.sendPacket(new SPacketChunkData(chunk, 65535));
            playerChunkMap.getWorldServer().getEntityTracker().sendLeashedEntitiesInChunk(player, chunk);
            flushable.setAutoFlush(true);
        }
    }
}
