package ilib.asm.nx;

import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import javax.annotation.Nullable;

/**
 * @author Roj233
 * @since 2022/4/27 12:39
 */
@Nixim("/")
class NxChunkSave extends WorldServer {
	@Shadow
	private PlayerChunkMap playerChunkMap;

	public NxChunkSave() {
		super(null, null, null, 0, null);
	}

	@Inject("/")
	public void saveAllChunks(boolean all, @Nullable IProgressUpdate cb) throws MinecraftException {
		ChunkProviderServer cp = this.getChunkProvider();
		if (cp.canSave() && MCHooks.shouldSaveChunk(all)) {
			if (cb != null) {
				cb.displaySavingString("Saving level");
			}

			this.saveLevel();
			if (cb != null) {
				cb.displayLoadingString("Saving chunks");
			}

			cp.saveChunks(all);
			MinecraftForge.EVENT_BUS.post(new WorldEvent.Save(this));
			for (Chunk chunk : cp.getLoadedChunks()) {
				if (chunk != null && !this.playerChunkMap.contains(chunk.x, chunk.z)) {
					cp.queueUnload(chunk);
				}
			}
		}
	}
}
