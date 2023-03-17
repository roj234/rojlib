package ilib.asm.nx.client.crd;

import ilib.asm.util.MCHooksClient;
import ilib.asm.util.MyBlockInfo;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * @author Roj233
 * @since 2022/4/22 19:11
 */
@Nixim(value = "net.minecraftforge.client.model.pipeline.BlockInfo", copyItf = true)
class NxBlockInfo implements MyBlockInfo {
	@Shadow("/")
	private IBlockAccess world;
	@Shadow("/")
	private IBlockState state;
	@Shadow("/")
	private BlockPos blockPos;
	@Shadow("/")
	private boolean[][][] t;
	@Shadow("/")
	private int[][][] s;
	@Shadow("/")
	private int[][][] b;
	@Shadow("/")
	private float[][][][] skyLight;
	@Shadow("/")
	private float[][][][] blockLight;
	@Shadow("/")
	private float[][][] ao;
	@Shadow("/")
	private int[] packed;
	@Shadow("/")
	private boolean full;

	@Shadow("/")
	private float combine(int c, int s1, int s2, int s3, boolean t0, boolean t1, boolean t2, boolean t3) {
		return 0;
	}

	@Inject("/")
	public void updateLightMatrix() {
		BlockPos.MutableBlockPos tmp = MCHooksClient.get();
		int x11 = blockPos.getX() - 1;
		int y11 = blockPos.getY() - 1;
		int z11 = blockPos.getZ() - 1;

		int x;
		int y;
		int z;
		IBlockAccess world = this.world;
		for (x = 0; x <= 2; ++x) {
			boolean[][] transparent1 = t[x];
			int[][] skyLight1 = s[x];
			int[][] blockLight1 = b[x];

			for (y = 0; y <= 2; ++y) {
				boolean[] transparent2 = transparent1[y];
				int[] skyLight2 = skyLight1[y];
				int[] blockLight2 = blockLight1[y];

				for (z = 0; z <= 2; ++z) {
					BlockPos pos = tmp.setPos(x11 + x, y11 + y, z11 + z);
					IBlockState state = world.getBlockState(pos);
					transparent2[z] = state.getLightOpacity(world, pos) < 15;
					int l = state.getPackedLightmapCoords(world, pos);
					skyLight2[z] = l >> 20 & 15;
					blockLight2[z] = l >> 4 & 15;
					ao[x][y][z] = state.getAmbientOcclusionLightValue();
				}
			}
		}

		int[][][] s = this.s;
		int[][][] b = this.b;
		for (EnumFacing side : EnumFacing.VALUES) {
			if (!state.doesSideBlockRendering(world, blockPos, side)) {
				int y1 = side.getXOffset() + 1;
				int z1 = side.getYOffset() + 1;
				int sxyz = side.getZOffset() + 1;
				s[y1][z1][sxyz] = Math.max(s[1][1][1] - 1, s[y1][z1][sxyz]);
				b[y1][z1][sxyz] = Math.max(b[1][1][1] - 1, b[y1][z1][sxyz]);
			}
		}

		boolean[][][] t = this.t;
		float[][][][] sl = skyLight;
		float[][][][] bl = blockLight;
		for (x = 0; x < 4; x += 2) {
			for (y = 0; y < 4; y += 2) {
				for (z = 0; z < 4; z += 2) {
					int sxyz = s[x][y][z];
					int bxyz = b[x][y][z];
					boolean txyz = t[x][y][z];
					int sxz = s[x][1][z];
					int sxy = s[x][y][1];
					int bxz = b[x][1][z];
					int bxy = b[x][y][1];
					boolean txz = t[x][1][z];
					boolean txy = t[x][y][1];

					int x9 = x >>> 1, y9 = y >>> 1, z9 = z >>> 1;

					int sx = s[x][1][1];
					int bx = b[x][1][1];
					boolean tx = t[x][1][1];
					boolean f = !txz & !txy;
					sl[0][x9][y9][z9] = combine(sx, sxz, sxy, f ? sx : sxyz, tx, txz, txy, f ? tx : txyz);
					bl[0][x9][y9][z9] = combine(bx, bxz, bxy, f ? bx : bxyz, tx, txz, txy, f ? tx : txyz);

					int syz = s[1][y][z];
					int byz = b[1][y][z];
					boolean tyz = t[1][y][z];
					int sy = s[1][y][1];
					int by = b[1][y][1];
					boolean ty = t[1][y][1];
					f = !txy & !tyz;
					sl[1][x9][y9][z9] = combine(sy, sxy, syz, f ? sy : sxyz, ty, txy, tyz, f ? ty : txyz);
					bl[1][x9][y9][z9] = combine(by, bxy, byz, f ? by : bxyz, ty, txy, tyz, f ? ty : txyz);

					int sz = s[1][1][z];
					int bz = b[1][1][z];
					boolean tz = t[1][1][z];
					f = !tyz & !txz;
					sl[2][x9][y9][z9] = combine(sz, syz, sxz, f ? sz : sxyz, tz, tyz, txz, f ? tz : txyz);
					bl[2][x9][y9][z9] = combine(bz, byz, bxz, f ? bz : bxyz, tz, tyz, txz, f ? tz : txyz);
				}
			}
		}
	}

	@Inject("/")
	public void updateFlatLighting() {
		BlockPos.MutableBlockPos tmp = MCHooksClient.get();
		full = state.isFullCube();
		packed[0] = state.getPackedLightmapCoords(world, blockPos);
		EnumFacing[] values = EnumFacing.VALUES;
		for (int i = 0; i < values.length; ) {
			EnumFacing side = values[i++];
			packed[i] = state.getPackedLightmapCoords(world, tmp.setPos(blockPos).move(side));
		}
	}
}
