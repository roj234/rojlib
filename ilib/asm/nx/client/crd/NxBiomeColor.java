package ilib.asm.nx.client.crd;

import ilib.Config;
import ilib.asm.util.MCHooksClient;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * @author Roj233
 * @since 2022/4/22 21:46
 */
@Nixim("net.minecraft.world.biome.BiomeColorHelper")
class NxBiomeColor {
	@Inject
	public static int getGrassColorAtPos(IBlockAccess lvt0_0, BlockPos lvt1_0) {
		return MCHooksClient.get().getBlendedColorMultiplier(lvt0_0, lvt1_0, MCHooksClient.GRASS, Config.biomeBlendRegion);
	}

	@Inject
	public static int getFoliageColorAtPos(IBlockAccess lvt0_0, BlockPos lvt1_0) {
		return MCHooksClient.get().getBlendedColorMultiplier(lvt0_0, lvt1_0, MCHooksClient.FOLIAGE, Config.biomeBlendRegion);
	}

	@Inject
	public static int getWaterColorAtPos(IBlockAccess lvt0_0, BlockPos lvt1_0) {
		return MCHooksClient.get().getBlendedColorMultiplier(lvt0_0, lvt1_0, MCHooksClient.WATER, Config.biomeBlendRegion);
	}
}
