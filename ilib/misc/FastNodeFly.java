package ilib.misc;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.math.MathUtils;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

/**
 * @author Roj233
 * @since 2022/5/17 0:43
 */
@Nixim("net.minecraft.pathfinding.FlyingNodeProcessor")
class FastNodeFly extends FastNodeWalk {
	@Inject("/")
	public PathPoint getStart() {
		BlockPos.PooledMutableBlockPos pmb = BlockPos.PooledMutableBlockPos.retain().setPos(entity);

		AxisAlignedBB box = entity.getEntityBoundingBox();
		int y;
		if (getCanSwim() && entity.isInWater()) {
			pmb.setY(y = (int) box.minY);

			Block block = blockaccess.getBlockState(pmb).getBlock();
			while (block == Blocks.FLOWING_WATER || block == Blocks.WATER) {
				pmb.setY(++y);
				block = blockaccess.getBlockState(pmb).getBlock();
			}
		} else {
			pmb.setY(y = MathHelper.floor(box.minY + 0.5D));
		}

		PathNodeType type = getPathNodeType(entity, pmb);
		if (entity.getPathPriority(type) < 0.0F) {
			for (int i = 0; i < 4; i++) {
				int x = MathUtils.floor((i & 2) == 0 ? box.minX : box.maxX);
				int z = MathUtils.floor((i & 1) == 0 ? box.minZ : box.maxZ);
				type = getPathNodeType(entity, x, y, z);
				if (entity.getPathPriority(type) >= 0.0F) {
					pmb.release();
					return openPoint(x, y, z);
				}
			}
		}

		PathPoint p = openPoint(pmb.getX(), y, pmb.getZ());
		pmb.release();
		return p;
	}

	@Inject("/")
	public int findPathOptions(PathPoint[] options, PathPoint src, PathPoint dst, float max) {
		int i = 0;
		int flag = 0;
		PathPoint p = openPoint(src.x, src.y, src.z + 1);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.costMalus != 0.0F) flag |= 1;

		p = openPoint(src.x - 1, src.y, src.z);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.costMalus != 0.0F) flag |= 2;

		p = openPoint(src.x + 1, src.y, src.z);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.costMalus != 0.0F) flag |= 4;

		p = openPoint(src.x, src.y, src.z - 1);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.costMalus != 0.0F) flag |= 8;

		p = openPoint(src.x, src.y + 1, src.z);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.costMalus != 0.0F) flag |= 16;

		p = openPoint(src.x, src.y - 1, src.z);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.costMalus != 0.0F) flag |= 32;

		if ((flag & 10) == 10) {
			p = openPoint(src.x - 1, src.y, src.z - 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 12) == 12) {
			p = openPoint(src.x + 1, src.y, src.z - 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 3) == 3) {
			p = openPoint(src.x - 1, src.y, src.z + 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 5) == 5) {
			p = openPoint(src.x + 1, src.y, src.z + 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 24) == 24) {
			p = openPoint(src.x, src.y + 1, src.z - 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 17) == 17) {
			p = openPoint(src.x, src.y + 1, src.z + 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 20) == 20) {
			p = openPoint(src.x + 1, src.y + 1, src.z);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 18) == 18) {
			p = openPoint(src.x - 1, src.y + 1, src.z);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 40) == 40) {
			p = openPoint(src.x, src.y - 1, src.z - 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 33) == 33) {
			p = openPoint(src.x, src.y - 1, src.z + 1);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 36) == 36) {
			p = openPoint(src.x + 1, src.y - 1, src.z);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 34) == 34) {
			p = openPoint(src.x - 1, src.y - 1, src.z);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		return i;
	}

	@Inject(value = "/", at = Inject.At.REMOVE)
	public PathNodeType getPathNodeType(IBlockAccess w, int x, int y, int z, EntityLiving a, int b, int c, int d, boolean e, boolean f) {
		// 与WalkNodeProcessor的代码居然相同？
		return null;
	}

	@Inject("/")
	public PathNodeType getPathNodeType(IBlockAccess w, int x, int y, int z) {
		PathNodeType type = getPathNodeTypeRaw(w, x, y, z);
		if (type == PathNodeType.OPEN && y >= 1) {
			PathNodeType type1 = getPathNodeTypeRaw(w, x, y - 1, z);
			switch (type1) {
				case WALKABLE:
				case OPEN:
				case WATER:
					type = PathNodeType.OPEN;
					break;
				case LAVA:
					type = PathNodeType.DAMAGE_FIRE;
					break;
				default:
					if (w.getBlockState(tmp.setPos(x, y - 1, z)).getBlock() == Blocks.MAGMA) {
						type = PathNodeType.DAMAGE_FIRE;
					} else {
						type = PathNodeType.WALKABLE;
					}
					break;
				case DAMAGE_FIRE:
				case DAMAGE_CACTUS:
				case DAMAGE_OTHER:
					type = type1;
					break;
			}
		}

		return checkNeighborBlocks(w, x, y, z, type);
	}

	@Shadow
	private PathNodeType getPathNodeType(EntityLiving entity, BlockPos pos) {
		return null;
	}

	@Shadow
	private PathNodeType getPathNodeType(EntityLiving entity, int x, int y, int z) {
		return null;
	}
}