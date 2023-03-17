package ilib.asm.nx;

import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

/**
 * @author Roj234
 * @since 2022/9/5 0005 10:36
 */
@Nixim("net.minecraft.world.biome.Biome")
class BiomeTempCache {
	@Shadow
	static NoiseGeneratorPerlin TEMPERATURE_NOISE;
	@Copy(staticInitializer = "a", unique = true, targetIsFinal = true)
	private static ThreadLocal<Long2FloatLinkedOpenHashMap> betterTempCache;

	static void a() {
		betterTempCache = ThreadLocal.withInitial(() -> {
			Long2FloatLinkedOpenHashMap map = new Long2FloatLinkedOpenHashMap(1024, 0.25F) {
				@Override
				protected void rehash(int n) {}
			};
			map.defaultReturnValue(Float.NaN);
			return map;
		});
	}

	@Inject
	public float getTemperature(BlockPos pos) {
		if (pos.getY() > 64) {
			Long2FloatLinkedOpenHashMap map = betterTempCache.get();

			float f = map.get(pos.toLong());
			if (f == f) return f;

			f = (float) (TEMPERATURE_NOISE.getValue((pos.getX() / 8.0F), (pos.getZ() / 8.0F)) * 4.0);
			f = getDefaultTemperature() - (f + pos.getY() - 64.0F) * 0.05F / 30.0F;
			map.put(pos.toLong(), f);
			return f;
		} else {
			return getDefaultTemperature();
		}
	}

	@Shadow
	private float getDefaultTemperature() {return 0;}
}
