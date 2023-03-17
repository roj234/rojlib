package ilib.asm.nx.client.crd;

import ilib.asm.util.MCHooksClient;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;

/**
 * @author Roj233
 * @since 2022/4/22 19:42
 */
@Nixim("/")
class NxChunkCache extends ChunkCache {
	NxChunkCache() {
		super(null, null, null, 0);
	}

	@Shadow("/")
	private boolean withinBounds(int x, int z) {
		return false;
	}

	@Inject
	private int getLightForExt(EnumSkyBlock type, BlockPos pos) {
		if (type == EnumSkyBlock.SKY && !world.provider.hasSkyLight()) {
			return 0;
		} else if (pos.getY() >= 0 && pos.getY() < 256) {
			int x = (pos.getX() >> 4) - chunkX;
			int z = (pos.getZ() >> 4) - chunkZ;
			if (withinBounds(x, z)) {
				Chunk c = chunkArray[x][z];
				if (c != null) {
					if (c.getBlockState(pos).useNeighborBrightness()) {
						int l = 0;
						MCHooksClient rep = MCHooksClient.get();
						for (EnumFacing face : EnumFacing.VALUES) {
							int k = getLightFor(type, rep.setPos(pos).move(face));
							if (k > l) l = k;

							if (l >= 15) break;
						}

						return l;
					} else {
						return c.getLightFor(type, pos);
					}
				}
			}
		}
		return type.defaultLightValue;
	}
}
