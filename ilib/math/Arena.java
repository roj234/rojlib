package ilib.math;

import net.minecraft.util.math.BlockPos;

/**
 * @author Roj234
 * @since 2021/1/2 15:22
 */
public class Arena {
	protected BlockPos p1 = null;
	protected BlockPos p2 = null;

	public Arena() {
	}

	public Section toSection() {
		if (isOK()) return new Section(p1, p2);
		return null;
	}

	public int getSelectionSize() {
		if (isOK()) return toSection().volume();
		return -1;
	}

	public boolean isOK() {
		return p1 != null && p2 != null;
	}

	public BlockPos getP1() {
		return p1;
	}

	public void setPos1(BlockPos p1) {
		this.p1 = p1;
	}

	public BlockPos getP2() {
		return p2;
	}

	public void setPos2(BlockPos p2) {
		this.p2 = p2;
	}
}