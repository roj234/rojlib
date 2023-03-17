package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

/**
 * @author Roj233
 * @since 2022/5/6 16:54
 */
@Nixim("/")
abstract class NoAlloc extends ChunkProviderServer {
	public NoAlloc() {
		super(null, null, null);
	}

	@Inject("/")
	public boolean saveChunks(boolean all) {
		int i = 0;
		for (Chunk chunk : this.loadedChunks.values()) {
			if (all) {
				this.saveChunkExtraData(chunk);
			}

			if (chunk.needsSaving(all)) {
				this.saveChunkData(chunk);
				chunk.setModified(false);
				if (++i == 24 && !all) {
					return false;
				}
			}
		}

		return true;
	}

	@Shadow
	private void saveChunkExtraData(Chunk chunkIn) {}

	@Shadow
	private void saveChunkData(Chunk chunkIn) {}
}
