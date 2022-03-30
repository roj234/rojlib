package ilib.asm.nixim.client;

import ilib.asm.util.MCHooks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

/**
 * @author solo6975
 * @since 2022/4/1 0:50
 */
@Nixim("net.minecraft.block.Block")
class NxNoPosAlloc {
    @Inject("func_176225_a")
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (MCHooks.debugRenderAllSide) return true;

        AxisAlignedBB aabb = state.getBoundingBox(world, pos);
        switch(side.ordinal()) {
            case 0:
                if (aabb.minY > 0.0D) {
                    return true;
                }
                break;
            case 1:
                if (aabb.maxY < 1.0D) {
                    return true;
                }
                break;
            case 2:
                if (aabb.minZ > 0.0D) {
                    return true;
                }
                break;
            case 3:
                if (aabb.maxZ < 1.0D) {
                    return true;
                }
                break;
            case 4:
                if (aabb.minX > 0.0D) {
                    return true;
                }
                break;
            case 5:
                if (aabb.maxX < 1.0D) {
                    return true;
                }
        }

        BlockPos.PooledMutableBlockPos p1 = BlockPos.PooledMutableBlockPos.retain(pos).move(side);
        boolean flag = !world.getBlockState(p1).doesSideBlockRendering(world, p1, side.getOpposite());
        p1.release();
        return flag;
    }
}
