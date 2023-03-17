package ilib.asm.util;

import roj.collect.AbstractIterator;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/23 0:15
 */
public class MergedItr extends AbstractIterator<Chunk> {
	final ChunkProviderServer w;
	final Iterator<ChunkPos> pers;
	final Iterator<Chunk> chunkIterator;

	public MergedItr(WorldServer w, Iterator<ChunkPos> persistent, Iterator<Chunk> chunkIterator) {
		this.w = w.getChunkProvider();
		this.pers = persistent;
		this.chunkIterator = chunkIterator;
	}

	/**
	 * @return true if currentObject is updated, false if not elements
	 */
	@Override
	public boolean computeNext() {
		if (pers.hasNext()) {
			ChunkPos pos = pers.next();
			result = w.loadChunk(pos.x, pos.z);
			return true;
		}

		boolean flag = chunkIterator.hasNext();
		if (flag) {
			result = chunkIterator.next();
		}
		return flag;
	}
}
