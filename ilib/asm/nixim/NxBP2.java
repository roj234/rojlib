package ilib.asm.nixim;

import ilib.asm.util.MCReplaces;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/1 0:50
 */
@Nixim("net.minecraft.block.Block")
class NxBP2 {
    @Inject("func_185492_a")
    protected static void addCollisionBoxToList(BlockPos pos, AxisAlignedBB entity, List<AxisAlignedBB> list, @Nullable AxisAlignedBB box) {
        if (box != null && 0 != box.getAverageEdgeLength()) {
            if (entity.intersects(box.minX+pos.getX(), box.minY+pos.getY(), box.minZ+pos.getZ(),
                                  box.maxX+pos.getX(), box.maxY+pos.getY(), box.maxZ+pos.getZ())) {
                list.add(box.offset(pos));
            }
        }
    }

    @Inject("func_176225_a")
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (MCReplaces.debugRenderAllSide) return true;

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

        BlockPos.MutableBlockPos p1 = MCReplaces.get().setPos(pos).move(side);
        return !world.getBlockState(p1).doesSideBlockRendering(world, p1, side.getOpposite());
    }
}
