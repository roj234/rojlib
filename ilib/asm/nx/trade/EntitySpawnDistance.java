package ilib.asm.nx.trade;

import ilib.asm.rpl.MobSpawn;
import roj.asm.nixim.Inject;
import roj.asm.nixim.NiximSystem;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

public class EntitySpawnDistance extends MobSpawn {
	@Inject(at = Inject.At.HEAD, value = "/")
	private static void getRandomSpawnMobAt(WorldServer world, BlockPos pos) {
		if (DistanceUtil.isInClaimedChunk(world, pos)) {
			NiximSystem.SpecMethods.$$$VALUE_V();
			return;
		}

		int maxHeight = DistanceUtil.maxLivingEntitySpawnDistanceY;
		int maxDistanceSquare = DistanceUtil.maxLivingEntitySpawnDistanceSquare;

		if (DistanceUtil.isNearPlayer(world, pos, maxHeight, maxDistanceSquare)) {
			NiximSystem.SpecMethods.$$$VALUE_V();
		}
	}
}
