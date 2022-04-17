package ilib.asm.nixim;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/27 15:59
 */
@Nixim("net.minecraft.block.BlockLeaves")
abstract class NxThroughLeaves extends BlockLeaves {
    @Override
    @Inject("/")
    public boolean isPassable(IBlockAccess worldIn, BlockPos pos) {
        return true;
    }

    @Override
    @Inject("/")
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase lv = (EntityLivingBase)entity;

            if (lv.fallDistance > 5f) {
                world.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                                SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1, 0.5f);
                world.setBlockToAir(pos);
            }

            else if (world.isRemote && world.getTotalWorldTime() % 6 == 0 &&
                    (lv.posX != lv.prevPosX ||
                            lv.posY != lv.prevPosY ||
                            lv.posZ != lv.prevPosZ)) {
                world.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                                SoundEvents.BLOCK_GRASS_HIT, SoundCategory.BLOCKS, 1, 0.5f);
            }

            if (lv.onGround) {
                lv.motionX *= 0.85;
                lv.motionY *= 0.85;
                lv.motionZ *= 0.85;
            }

            if (lv.fallDistance > 3) {
                lv.fallDistance -= 3;
                PotionEffect pe = lv.getActivePotionEffect(MobEffects.JUMP_BOOST);
                float modifier = pe == null ? 1 : Math.max(0, 1 - pe.getAmplifier() / 3f);
                lv.attackEntityFrom(DamageSource.CRAMMING, lv.fallDistance * modifier * 0.6f);
            }

            if (lv.fallDistance > 1) lv.fallDistance = 1;
        }
    }

    @Override
    @Inject("/")
    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {}

    @Nullable
    @Override
    @Inject("/")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }
}
