package ilib.asm.nx;

import ilib.Config;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Shadow;
import roj.util.EmptyArrays;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import net.minecraftforge.event.ForgeEventFactory;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Roj234
 * @since 2022/10/26 0026 9:27
 */
public class FastExplosion extends Explosion {
	@Shadow
	private boolean causesFire, damagesTerrain;
	@Shadow
	private Random random;
	@Shadow
	private World world;
	@Shadow
	private double x, y, z;
	@Shadow
	private Entity exploder;
	@Shadow
	private float size;
	@Shadow
	private LongArrayList affectedBlockPositions1;
	@Shadow
	private Map<EntityPlayer, Vec3d> playerKnockbackMap;
	@Shadow
	private Vec3d position;

	public FastExplosion(World worldIn, Entity entityIn, double x, double y, double z, float size, boolean flaming, boolean damagesTerrain) {
		super(worldIn, entityIn, x, y, z, size, flaming, damagesTerrain);
	}

	@Inject("/")
	public void doExplosionA() {
		LongOpenHashSet affects = new LongOpenHashSet();

		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		for (int j = 0; j < 16; ++j) {
			for (int k = 0; k < 16; ++k) {
				for (int l = 0; l < 16; ++l) {
					if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
						double d0 = (j / 15.0F * 2.0F - 1.0F);
						double d1 = (k / 15.0F * 2.0F - 1.0F);
						double d2 = (l / 15.0F * 2.0F - 1.0F);
						double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
						d0 /= d3;
						d1 /= d3;
						d2 /= d3;
						float f = size * (0.7F + world.rand.nextFloat() * 0.6F);
						double d4 = x;
						double d6 = y;
						double d8 = z;

						while (f > 0) {
							IBlockState state = world.getBlockState(mp.setPos(d4, d6, d8));
							if (state.getMaterial() != Material.AIR) {
								float rs = exploder != null ? exploder.getExplosionResistance(this, world, mp,
									state) : state.getBlock().getExplosionResistance(world, mp, null, this);
								f -= (rs + 0.3F) * 0.3F;
							}

							if (f > 0 && (exploder == null || exploder.canExplosionDestroyBlock(this, world, mp, state, f))) {
								affects.add(mp.toLong());
							}

							d4 += d0 * 0.3;
							d6 += d1 * 0.3;
							d8 += d2 * 0.3;
							f -= 0.225F;
						}
					}
				}
			}
		}

		affectedBlockPositions1.addAll(affects);

		float strength = size * 2;

		int a = MathHelper.floor(x - strength - 1);
		int b = MathHelper.floor(x + strength + 1);
		int c = MathHelper.floor(y - strength - 1);
		int d = MathHelper.floor(y + strength + 1);
		int e = MathHelper.floor(z - strength - 1);
		int f = MathHelper.floor(z + strength + 1);
		AxisAlignedBB box = new AxisAlignedBB(a, c, e, b, d, f);
		Vec3d posv = new Vec3d(x, y, z);

		if (Config.fastExplosion2) {
			world.getEntitiesInAABBexcluding(exploder, box, entity -> {
				handleEntityKnockback(entity, strength, posv);
				return false;
			});
		} else {
			List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(exploder, box);
			ForgeEventFactory.onExplosionDetonate(world, this, list, strength);

			for (int i = 0; i < list.size(); ++i) {
				handleEntityKnockback(list.get(i), strength, posv);
			}
		}
	}

	@Copy
	private void handleEntityKnockback(Entity entity, float strength, Vec3d posv) {
		if (entity.isImmuneToExplosions()) return;
		double d12 = entity.getDistance(x, y, z) / strength;
		if (d12 <= 1) {
			double d5 = entity.posX - x;
			double d7 = entity.posY + entity.getEyeHeight() - y;
			double d9 = entity.posZ - z;
			double d13 = MathHelper.sqrt(d5 * d5 + d7 * d7 + d9 * d9);
			if (d13 != 0.0) {
				d5 /= d13;
				d7 /= d13;
				d9 /= d13;
				double d14 = world.getBlockDensity(posv, entity.getEntityBoundingBox());
				double d10 = (1.0 - d12) * d14;
				entity.attackEntityFrom(DamageSource.causeExplosionDamage(this), ((int) ((d10 * d10 + d10) / 2.0 * 7.0 * strength + 1.0)));
				double d11 = d10;
				if (entity instanceof EntityLivingBase) {
					d11 = EnchantmentProtection.getBlastDamageReduction((EntityLivingBase) entity, d10);
				}

				entity.motionX += d5 * d11;
				entity.motionY += d7 * d11;
				entity.motionZ += d9 * d11;
				if (entity instanceof EntityPlayer) {
					EntityPlayer p = (EntityPlayer) entity;
					if (!p.isSpectator() && (!p.isCreative() || !p.capabilities.isFlying)) {
						playerKnockbackMap.put(p, new Vec3d(d5 * d10, d7 * d10, d9 * d10));
					}
				}
			}
		}
	}

	@Inject("/")
	public void doExplosionB(boolean spawnParticles) {
		world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4,
			(1 + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F) * 0.7F);
		world.spawnParticle(
			size >= 2 && damagesTerrain ? EnumParticleTypes.EXPLOSION_HUGE : EnumParticleTypes.EXPLOSION_LARGE, x, y, z, 1,
			0, 0, EmptyArrays.INTS);

		if (!damagesTerrain && !causesFire) return;

		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();

		int NUM_X_BITS = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
		int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_X_BITS;
		int X_SHIFT = NUM_X_BITS + NUM_Y_BITS;

		for (LongListIterator itr = affectedBlockPositions1.iterator(); itr.hasNext(); ) {
			long l = itr.nextLong();

			int x = (int)(l << 64 - X_SHIFT - NUM_X_BITS >> 64 - NUM_X_BITS);
			int y = (int)(l << 64 - NUM_X_BITS - NUM_Y_BITS >> 64 - NUM_Y_BITS);
			int z = (int)(l << 64 - NUM_X_BITS >> 64 - NUM_X_BITS);

			IBlockState state = world.getBlockState(pos1.setPos(x,y,z));

			if (damagesTerrain) {
				if (spawnParticles) {
					double d0 = pos1.getX() + world.rand.nextFloat();
					double d1 = pos1.getY() + world.rand.nextFloat();
					double d2 = pos1.getZ() + world.rand.nextFloat();
					double d3 = d0 - x;
					double d4 = d1 - y;
					double d5 = d2 - z;
					double d6 = MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
					d3 /= d6;
					d4 /= d6;
					d5 /= d6;
					double d7 = 0.5 / (d6 / size + 0.1);
					d7 *= world.rand.nextFloat() * world.rand.nextFloat() + 0.3F;
					d3 *= d7;
					d4 *= d7;
					d5 *= d7;
					world.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, (d0 + x) / 2.0, (d1 + y) / 2.0,
						(d2 + z) / 2.0, d3, d4, d5);
					world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0, d1, d2, d3, d4, d5);
				}

				if (state.getMaterial() != Material.AIR) {
					Block block = state.getBlock();
					if (block.canDropFromExplosion(this)) {
						block.dropBlockAsItemWithChance(world, pos1, state, 1.0F / size, 0);
					}

					block.onBlockExploded(world, pos1, this);
				}
			}

			if (causesFire) {
				if (state.getMaterial() == Material.AIR &&
					world.getBlockState(pos1.move(EnumFacing.DOWN)).isFullBlock() &&
					random.nextInt(3) == 0) {
					world.setBlockState(pos1.move(EnumFacing.UP), Blocks.FIRE.getDefaultState());
				}
			}
		}

		pos1.release();
	}

	@Override
	public List<BlockPos> getAffectedBlockPositions() {
		return new AbstractList<BlockPos>() {
			@Override
			public BlockPos get(int i) {
				return BlockPos.fromLong(affectedBlockPositions1.getLong(i));
			}

			@Override
			public int size() {
				return affectedBlockPositions1.size();
			}
		};
	}
}
