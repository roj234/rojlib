package roj.plugins.minecraft.server.data;

import roj.math.Vec3i;

/**
 * @author Roj234
 * @since 2024/3/22 4:14
 */
public enum EnumFacing {
	DOWN(-1, new Vec3i(0, -1, 0)),
	UP(-1, new Vec3i(0, 1, 0)),
	NORTH(2, new Vec3i(0, 0, -1)),
	SOUTH(0, new Vec3i(0, 0, 1)),
	WEST(1, new Vec3i(-1, 0, 0)),
	EAST(3, new Vec3i(1, 0, 0));

	private final int idHorizontal;
	private final Vec3i vector;

	public static final EnumFacing[] VALUES = values();
	private static final EnumFacing[] HORIZONTAL = {NORTH,SOUTH,WEST,EAST};

	EnumFacing(int horizonal, Vec3i vector) {
		this.idHorizontal = horizonal;
		this.vector = vector;
	}

	public int getHorizontal() {return this.idHorizontal;}
	public EnumFacing.Axis getDirection() {return Axis.VALUES[2 - ordinal()/2];}

	public static enum Axis {
		X,Y,Z;
		static final Axis[] VALUES = values();
	}

	public EnumFacing getOpposite() {return VALUES[ordinal() ^ 1];}

	public static EnumFacing byId(int id) {return VALUES[Math.abs(id % VALUES.length)];}
	public static EnumFacing fromHorizontal(int id) {return HORIZONTAL[Math.abs(id % HORIZONTAL.length)];}
	public float asRotation() {return ((idHorizontal & 3) * 90);}
	public Vec3i getVector() { return this.vector; }
}