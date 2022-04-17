package ilib.asm.nixim;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj233
 * @since 2022/4/23 0:50
 */
@Nixim("net.minecraft.entity.Entity")
abstract class NxBP1 extends Entity {
    public NxBP1() {
        super(null);
    }

    @Inject("/")
    public boolean isInsideOfMaterial(Material mat) {
        if (this.getRidingEntity() instanceof EntityBoat) {
            return false;
        } else {
            double d0 = posY + getEyeHeight();
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(posX, d0, posZ);
            IBlockState state = world.getBlockState(pos);
            Boolean result = state.getBlock().isEntityInsideMaterial(this.world, pos, state, this, d0, mat, true);
            if (result != null) {
                pos.release();
                return result;
            } else {
                try {
                    return state.getMaterial() == mat && ForgeHooks.isInsideOfMaterial(mat, this, pos);
                } finally {
                    pos.release();
                }
            }
        }
    }

    // isOnLadder

    @Inject("/")
    @SideOnly(Side.CLIENT)
    public int getBrightnessForRender() {
        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(MathHelper.floor(posX), MathHelper.floor(posY + getEyeHeight()), MathHelper.floor(posZ));
        int l;
        if (world.isBlockLoaded(pos)) {
            l = world.getCombinedLight(pos, 0);
        } else {
            l = 0;
        }
        pos.release();
        return l;
    }

    @Inject("/")
    public float getBrightness() {
        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(MathHelper.floor(posX), MathHelper.floor(posY + getEyeHeight()), MathHelper.floor(posZ));
        float l;
        if (world.isBlockLoaded(pos)) {
            l = world.getLightBrightness(pos);
        } else {
            l = 0;
        }
        pos.release();
        return l;
    }
}
