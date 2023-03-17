package ilib.asm.nx;

import com.google.common.base.Predicate;
import ilib.Config;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.SimpleList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

import static net.minecraft.world.World.MAX_ENTITY_RADIUS;

/**
 * @author Roj233
 * @since 2022/5/16 5:13
 */
@Nixim("/")
abstract class NxCollision2 extends EntityLivingBase {
	@Copy(unique = true)
	static List<Entity> collb;

	NxCollision2(World worldIn) {
		super(worldIn);
	}

	@Inject("/")
	protected void collideWithNearbyEntities() {
		if (world.isRemote) return;

		List<Entity> list = collb;
		if (list == null) {
			list = collb = new SimpleList<>();
		}

		AxisAlignedBB box = this.getEntityBoundingBox();
		int j2 = MathHelper.floor((box.minX - MAX_ENTITY_RADIUS) / 16.0D);
		int k2 = MathHelper.floor((box.maxX + MAX_ENTITY_RADIUS) / 16.0D);
		int l2 = MathHelper.floor((box.minZ - MAX_ENTITY_RADIUS) / 16.0D);
		int i3 = MathHelper.floor((box.maxZ + MAX_ENTITY_RADIUS) / 16.0D);

		Predicate<Entity> pred = EntitySelectors.getTeamCollisionPredicate(this);
		if (Config.noCollision) {
			Predicate<Entity> pred1 = pred;
			pred = entity -> (entity instanceof EntityMinecart || entity instanceof EntityPlayer) && pred1.test(entity);
		}

		World w = this.world;

		int max = w.getGameRules().getInt("maxEntityCramming");
		if (max <= 0) max = 32767;
		int total = 0;

		for (int x = j2; x <= k2; ++x) {
			for (int z = l2; z <= i3; ++z) {
				list.clear();
				w.getChunk(x, z).getEntitiesWithinAABBForEntity(this, box, list, pred);

				for (int i = 0; i < list.size(); i++) {
					Entity in = list.get(i);
					if (in.posX != posX && in.posZ != posZ) {
						collideWithEntity(in);
					}

					if (!in.isRiding()) max--;
					if (max <= 0) {
						if (rand.nextInt(4) == 0) {
							attackEntityFrom(DamageSource.CRAMMING, 6.0F);
						}
						max = 32767;
					}
				}
			}
		}
	}
}
