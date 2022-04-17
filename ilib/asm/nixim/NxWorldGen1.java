package ilib.asm.nixim;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * @author Roj233
 * @since 2022/4/28 22:09
 */
//!!AT [["net.minecraft.world.chunk.storage.ExtendedBlockStorage", ["field_76684_a"]]]
@Nixim("net.minecraft.world.chunk.Chunk")
public class NxWorldGen1 extends Chunk {
    public NxWorldGen1(World worldIn, int x, int z) {
        super(worldIn, x, z);
    }

    @Inject(value = "<init>", at = Inject.At.REPLACE)
    public void xxx(World worldIn, ChunkPrimer primer, int x, int z) {
        $$$CONSTRUCTOR_THIS(worldIn, x, z);
        boolean flag = worldIn.provider.hasSkyLight();

        ExtendedBlockStorage[] stgs = this.getBlockStorageArray();
        ExtendedBlockStorage tmp = new ExtendedBlockStorage(0, flag);
        boolean used = false;

        for(int y = 0; y < 16; ++y) {
            for(int x1 = 0; x1 < 16; ++x1) {
                for(int z1 = 0; z1 < 16; ++z1) {
                    int end = (y+1)<<4;
                    for (int y1 = y<<4; y1 < end; y1++) {
                        IBlockState state = primer.getBlockState(x1, y1, z1);
                        if (state.getMaterial() != Material.AIR) {
                            tmp.set(x1, y1 & 15, z1, state);
                            used = true;
                        }
                    }
                }
            }

            if (used) {
                tmp.yBase = y<<4;
                stgs[y] = tmp;
                tmp = new ExtendedBlockStorage(0, flag);
                used = false;
            }
        }
    }

    private void $$$CONSTRUCTOR_THIS(World in, int x, int z) {

    }
}
