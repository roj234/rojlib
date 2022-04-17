package ilib.asm;

import roj.collect.SimpleList;
import roj.util.FastThreadLocal;

import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;

/**
 * @author Roj233
 * @since 2022/4/22 22:43
 */
public class LMReplace extends BlockPos.MutableBlockPos {
    private boolean field_185350_f;
    private static final FastThreadLocal<SimpleList<LMReplace>> ul = FastThreadLocal.withInitial(SimpleList::new);

    private LMReplace(int _lvt_1_, int _lvt_2_, int _lvt_3_) {
        super(_lvt_1_, _lvt_2_, _lvt_3_);
    }

    public static LMReplace func_185346_s() {
        return func_185339_c(0, 0, 0);
    }

    public static LMReplace func_185345_c(double xIn, double yIn, double zIn) {
        return func_185339_c(MathHelper.floor(xIn), MathHelper.floor(yIn), MathHelper.floor(zIn));
    }

    public static LMReplace func_185342_g(Vec3i vec) {
        return func_185339_c(vec.getX(), vec.getY(), vec.getZ());
    }

    public static LMReplace func_185339_c(int xIn, int yIn, int zIn) {
        SimpleList<LMReplace> pool = ul.get();
        if (!pool.isEmpty()) {
            LMReplace p = pool.remove(pool.size() - 1);
            if (p != null && p.field_185350_f) {
                p.field_185350_f = false;
                p.setPos(xIn, yIn, zIn);
                return p;
            }
        }

        return new LMReplace(xIn, yIn, zIn);
    }

    public void func_185344_t() {
        SimpleList<LMReplace> pool = ul.get();
        if (!field_185350_f && pool.size() < 666) {
            pool.add(this);
            field_185350_f = true;
        } else {
            System.err.println("pool overflow " + pool.size());
        }
    }

    public LMReplace setPos(int _lvt_1_, int _lvt_2_, int _lvt_3_) {
        if (field_185350_f) {
            throw new IllegalStateException("PooledMutableBlockPosition modified after it was released.");
        }

        return (LMReplace)super.setPos(_lvt_1_, _lvt_2_, _lvt_3_);
    }

    public LMReplace setPos(Entity entityIn) {
        return (LMReplace)super.setPos(entityIn);
    }

    public LMReplace setPos(double xIn, double yIn, double zIn) {
        return (LMReplace)super.setPos(xIn, yIn, zIn);
    }

    public LMReplace setPos(Vec3i vec) {
        return (LMReplace)super.setPos(vec);
    }

    public LMReplace move(EnumFacing _lvt_1_) {
        return (LMReplace)super.move(_lvt_1_);
    }

    public LMReplace move(EnumFacing facing, int n) {
        return (LMReplace)super.move(facing, n);
    }

}
