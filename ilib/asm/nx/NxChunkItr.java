package ilib.asm.nx;

import com.google.common.collect.ImmutableSetMultimap;
import ilib.asm.util.MergedItr;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.ForgeChunkManager;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("/")
abstract class NxChunkItr extends World {
	protected NxChunkItr() {
		super(null, null, null, null, false);
	}

	@Override
	@Inject("/")
	public Iterator<Chunk> getPersistentChunkIterable(Iterator<Chunk> itr) {
		ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> persistentChunksFor = getPersistentChunks();
		if (!persistentChunksFor.isEmpty()) {
			return new MergedItr((WorldServer) (Object) this, persistentChunksFor.keys().iterator(), itr);
		} else {
			return itr;
		}
	}
}