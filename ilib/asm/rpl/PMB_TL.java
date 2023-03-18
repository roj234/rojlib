package ilib.asm.rpl;

import roj.collect.SimpleList;

import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;

/**
 * @author Roj233
 * @since 2022/4/22 22:43
 */
public final class PMB_TL extends BlockPos.MutableBlockPos {
	private boolean field_185350_f;
	private static final ThreadLocal<SimpleList<PMB_TL>> ul = ThreadLocal.withInitial(SimpleList::new);

	private PMB_TL(int _lvt_1_, int _lvt_2_, int _lvt_3_) {
		super(_lvt_1_, _lvt_2_, _lvt_3_);
	}

	public static PooledMutableBlockPos func_185346_s() {
		return func_185339_c(0, 0, 0);
	}

	public static PooledMutableBlockPos func_185345_c(double xIn, double yIn, double zIn) {
		return func_185339_c(MathHelper.floor(xIn), MathHelper.floor(yIn), MathHelper.floor(zIn));
	}

	public static PooledMutableBlockPos func_185342_g(Vec3i vec) {
		return func_185339_c(vec.getX(), vec.getY(), vec.getZ());
	}

	public static PooledMutableBlockPos func_185339_c(int xIn, int yIn, int zIn) {
		SimpleList<PMB_TL> pool = ul.get();
		if (!pool.isEmpty()) {
			PMB_TL p = pool.pop();
			if (p != null && p.field_185350_f) {
				p.field_185350_f = false;
				p.setPos(xIn, yIn, zIn);
				return (PooledMutableBlockPos) (Object) p;
			}
		}

		return (PooledMutableBlockPos) (Object) new PMB_TL(xIn, yIn, zIn);
	}

	public void func_185344_t() {
		SimpleList<PMB_TL> pool = ul.get();
		if (!field_185350_f && pool.size() < 666) {
			pool.add(this);
			field_185350_f = true;
		} else {
			System.err.println("pool overflow " + pool.size());
		}
	}

	public PooledMutableBlockPos func_181079_c(int _lvt_1_, int _lvt_2_, int _lvt_3_) {
		if (field_185350_f) {
			throw new IllegalStateException("PooledMutableBlockPosition modified after it was released.");
		}

		return (PooledMutableBlockPos) super.setPos(_lvt_1_, _lvt_2_, _lvt_3_);
	}

	public PooledMutableBlockPos func_189535_a(Entity entityIn) {
		return (PooledMutableBlockPos) this.setPos(entityIn.posX, entityIn.posY, entityIn.posZ);
	}

	public PooledMutableBlockPos func_189532_c(double xIn, double yIn, double zIn) {
		return (PooledMutableBlockPos) super.setPos(xIn, yIn, zIn);
	}

	public PooledMutableBlockPos func_189533_g(Vec3i vec) {
		return (PooledMutableBlockPos) super.setPos(vec);
	}

	public PooledMutableBlockPos func_189536_c(EnumFacing _lvt_1_) {
		return (PooledMutableBlockPos) super.move(_lvt_1_);
	}

	public PooledMutableBlockPos func_189534_c(EnumFacing facing, int n) {
		return (PooledMutableBlockPos) super.move(facing, n);
	}
}
