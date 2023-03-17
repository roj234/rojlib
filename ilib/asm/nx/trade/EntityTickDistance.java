package ilib.asm.nx.trade;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.NiximSystem;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

@Nixim("net.minecraft.world.World")
abstract class EntityTickDistance {
	@Shadow
	private Random rand;

	@Inject(at = Inject.At.HEAD)
	public void updateEntity(Entity entity) {
		World world = ((World) (Object) this);
		BlockPos pos = entity.getPosition();

		boolean isInClaimedChunk = DistanceUtil.isInClaimedChunk(world, pos);

		if (!(entity instanceof EntityLivingBase) || entity instanceof EntityPlayer) {
			if (!isInClaimedChunk && entity instanceof EntityItem && rand.nextInt(4) == 0) {
				return;
			}

			NiximSystem.SpecMethods.$$$VALUE_V();
			return;
		}

		int maxHeight = DistanceUtil.maxLivingEntityTickDistanceY;
		int maxDistanceSquare = DistanceUtil.maxLivingEntityTickDistanceSquare;

		if (DistanceUtil.isNearPlayer(world, pos, maxHeight, maxDistanceSquare)) {
			NiximSystem.SpecMethods.$$$VALUE_V();
			return;
		}

		// handle edge cases for entities outside radius

		if (isInClaimedChunk && ((EntityLivingBase) entity).deathTime > 0 || entity.isDead) {
			NiximSystem.SpecMethods.$$$VALUE_V();
		}
	}
}
