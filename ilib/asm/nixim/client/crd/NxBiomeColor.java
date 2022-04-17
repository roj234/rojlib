package ilib.asm.nixim.client.crd;

import ilib.asm.util.MCReplaces;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;

/**
 * @author Roj233
 * @since 2022/4/22 21:46
 */
@Nixim("net.minecraft.world.biome.BiomeColorHelper")
class NxBiomeColor {
    @Inject("func_180285_a")
    private static int getColorAtPos(IBlockAccess world, BlockPos pos, BiomeColorHelper.ColorResolver cr) {
        int r = 0;
        int g = 0;
        int b = 0;

        MCReplaces pos1 = MCReplaces.get();
        for (int i = 0; i < 9; i++) {
            pos1.setPos(pos.getX()-1 + i/3, pos.getY(), pos.getZ()-1 + i&3);
            int w = cr.getColorAtPos(world.getBiome(pos1), pos1);
            r += (w & 16711680) >> 16;
            g += (w & '\uff00') >> 8;
            b += w & 255;
        }

        return (r / 9 & 255) << 16 | (g / 9 & 255) << 8 | b / 9 & 255;
    }
}
