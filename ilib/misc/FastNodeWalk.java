package ilib.misc;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.math.MathUtils;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

import java.util.EnumSet;

/**
 * @author Roj233
 * @since 2022/5/16 22:13
 */
@Nixim("/")
class FastNodeWalk extends WalkNodeProcessor {
	@Copy(unique = true)
	BlockPos.MutableBlockPos tmp;

	@Inject(value = "<init>", at = Inject.At.TAIL)
	public void init() {
		tmp = new BlockPos.MutableBlockPos();
	}

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
		} else if (entity.onGround) {
			pmb.setY(y = MathHelper.floor(box.minY + 0.5D));
		} else {
			IBlockState st = blockaccess.getBlockState(pmb);
			while (st.getMaterial() == Material.AIR || st.getBlock().isPassable(blockaccess, pmb)) {
				if (pmb.getY() == 0) break;

				pmb.setY(pmb.getY() - 1);
				st = blockaccess.getBlockState(pmb);
			}

			y = pmb.getY() + 1;

			pmb.setY(y);
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
		int step = 0;
		PathNodeType type = this.getPathNodeType(entity, src.x, src.y + 1, src.z);
		if (entity.getPathPriority(type) >= 0.0F) {
			step = MathHelper.floor(Math.max(1.0F, this.entity.stepHeight));
		}

		BlockPos pos = tmp.setPos(src.x, src.y - 1, src.z);
		double height = src.y - (1.0D - blockaccess.getBlockState(pos).getBoundingBox(this.blockaccess, pos).maxY);

		int i = 0;
		int flag = 0;

		PathPoint p = getSafePoint(src.x, src.y, src.z + 1, step, height, EnumFacing.SOUTH);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.nodeType == PathNodeType.OPEN || p.costMalus != 0.0F) {
			flag |= 1;
		}

		p = getSafePoint(src.x - 1, src.y, src.z, step, height, EnumFacing.WEST);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.nodeType == PathNodeType.OPEN || p.costMalus != 0.0F) {
			flag |= 2;
		}

		p = getSafePoint(src.x + 1, src.y, src.z, step, height, EnumFacing.EAST);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.nodeType == PathNodeType.OPEN || p.costMalus != 0.0F) {
			flag |= 4;
		}

		p = getSafePoint(src.x, src.y, src.z - 1, step, height, EnumFacing.NORTH);
		if (p != null && !p.visited && p.distanceTo(dst) < max) {
			options[i++] = p;
		}
		if (p == null || p.nodeType == PathNodeType.OPEN || p.costMalus != 0.0F) {
			flag |= 8;
		}

		if ((flag & 10) == 10) {
			p = this.getSafePoint(src.x - 1, src.y, src.z - 1, step, height, EnumFacing.NORTH);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 12) == 12) {
			p = this.getSafePoint(src.x + 1, src.y, src.z - 1, step, height, EnumFacing.NORTH);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 3) == 3) {
			p = this.getSafePoint(src.x - 1, src.y, src.z + 1, step, height, EnumFacing.SOUTH);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		if ((flag & 5) == 5) {
			p = this.getSafePoint(src.x + 1, src.y, src.z + 1, step, height, EnumFacing.SOUTH);
			if (p != null && !p.visited && p.distanceTo(dst) < max) {
				options[i++] = p;
			}
		}

		return i;
	}

	@Inject("func_186332_a")
	private PathPoint getSafePoint(int x, int y, int z, int step, double height, EnumFacing facing) {
		BlockPos tmp = this.tmp.setPos(x, y - 1, z);

		if (y - (1.0D - blockaccess.getBlockState(tmp).getBoundingBox(blockaccess, tmp).maxY) - height > 1.125D) {
			return null;
		}

		PathPoint p = null;
		PathNodeType type = getPathNodeType(this.entity, x, y, z);
		float prio = this.entity.getPathPriority(type);
		if (prio >= 0.0F) {
			p = openPoint(x, y, z);
			p.nodeType = type;
			p.costMalus = Math.max(p.costMalus, prio);
		}

		double d1 = this.entity.width / 2.0D;

		switch (type) {
			case FENCE:
			case TRAPDOOR:
			case WALKABLE:
				return p;
			default:
				if (entity.width < 1.0F && p == null && step > 0) {
					p = getSafePoint(x, y + 1, z, step - 1, height, facing);
					if (p != null && (p.nodeType == PathNodeType.OPEN || p.nodeType == PathNodeType.WALKABLE)) {
						double dx = (x - facing.getXOffset()) + 0.5D;
						double dz = (z - facing.getZOffset()) + 0.5D;
						AxisAlignedBB box = new AxisAlignedBB(dx - d1, y + 0.001D, dz - d1, dx + d1, y + entity.height, dz + d1);
						AxisAlignedBB box1 = blockaccess.getBlockState(tmp).getBoundingBox(this.blockaccess, tmp);
						AxisAlignedBB box2 = box.expand(0.0D, box1.maxY - 0.002D, 0.0D);
						if (entity.world.collidesWithAnyBlock(box2)) p = null;
					}
				}
				break;
		}

		if (type == PathNodeType.OPEN) {
			AxisAlignedBB box = new AxisAlignedBB(x - d1 + 0.5D, y + 0.001D, z - d1 + 0.5D, x + d1 + 0.5D, y + this.entity.height, z + d1 + 0.5D);
			if (entity.world.collidesWithAnyBlock(box)) return null;

			if (entity.width >= 1.0F) {
				type = getPathNodeType(entity, x, y - 1, z);
				if (type == PathNodeType.BLOCKED) {
					p = openPoint(x, y, z);
					p.nodeType = PathNodeType.WALKABLE;
					p.costMalus = Math.max(p.costMalus, prio);
					return p;
				}
			}

			int fall = entity.getMaxFallHeight();
			while (y-- > 0) {
				if (fall-- <= 0) return null;

				type = getPathNodeType(entity, x, y, z);

				prio = entity.getPathPriority(type);
				if (prio < 0.0F) return null;

				if (type != PathNodeType.OPEN) {
					p = openPoint(x, y, z);
					p.nodeType = type;
					p.costMalus = Math.max(p.costMalus, prio);
					return p;
				}

			}
		}

		return p;
	}

	@Inject("/")
	public PathNodeType getPathNodeType(IBlockAccess w, int x, int y, int z, EntityLiving entity, int xSize, int ySize, int zSize, boolean breakDoor, boolean enterDoor) {
		EnumSet<PathNodeType> set = EnumSet.noneOf(PathNodeType.class);

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain().setPos(entity);

		currentEntity = entity;
		PathNodeType type = getPathNodeType(w, x, y, z, xSize, ySize, zSize, breakDoor, enterDoor, set, PathNodeType.BLOCKED, pos);
		currentEntity = null;

		pos.release();

		if (set.contains(PathNodeType.FENCE)) {
			return PathNodeType.FENCE;
		}

		PathNodeType curr = PathNodeType.BLOCKED;
		for (PathNodeType next : set) {
			if (entity.getPathPriority(next) < 0.0F) return next;

			if (entity.getPathPriority(next) >= entity.getPathPriority(curr)) {
				curr = next;
			}
		}

		if (type == PathNodeType.OPEN && entity.getPathPriority(curr) == 0.0F) {
			return PathNodeType.OPEN;
		} else {
			return curr;
		}
	}

	@Inject("/")
	public PathNodeType getPathNodeType(IBlockAccess w, int x, int y, int z) {
		PathNodeType type = this.getPathNodeTypeRaw(w, x, y, z);
		if (type == PathNodeType.OPEN && y >= 1) {
			PathNodeType type1 = getPathNodeTypeRaw(w, x, y - 1, z);
			switch (type1) {
				case WALKABLE:
				case OPEN:
				case WATER:
				case LAVA:
					type = PathNodeType.OPEN;
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

		return this.checkNeighborBlocks(w, x, y, z, type);
	}

	@Inject("/")
	protected PathNodeType getPathNodeTypeRaw(IBlockAccess w, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = tmp.setPos(x, y, z);

		IBlockState state = w.getBlockState(pos);
		Block block = state.getBlock();

		PathNodeType type = block.getAiPathNodeType(state, w, pos, this.currentEntity);
		if (type != null) return type;

		Material mat = state.getMaterial();
		if (mat == Material.AIR) return PathNodeType.OPEN;

		switch (block.getRegistryName().getPath()) {
			case "trapdoor":
			case "iron_trapdoor":
			case "waterlily":
				return PathNodeType.TRAPDOOR;
			case "fire":
				return PathNodeType.DAMAGE_FIRE;
			case "cactus":
				return PathNodeType.DAMAGE_CACTUS;
		}

		if (block instanceof BlockDoor) {
			if (state.getValue(BlockDoor.OPEN)) {
				return PathNodeType.DOOR_OPEN;
			} else if (mat == Material.WOOD) {
				return PathNodeType.DOOR_WOOD_CLOSED;
			} else if (mat == Material.IRON) {
				return PathNodeType.DOOR_IRON_CLOSED;
			}
		} else if (block instanceof BlockRailBase) {
			return PathNodeType.RAIL;
		} else if (block instanceof BlockFence || block instanceof BlockWall || (block instanceof BlockFenceGate && !state.getValue(BlockFenceGate.OPEN))) {
			return PathNodeType.FENCE;
		}

		if (mat == Material.WATER) {
			return PathNodeType.WATER;
		} else if (mat == Material.LAVA) {
			return PathNodeType.LAVA;
		} else {
			return block.isPassable(w, pos) ? PathNodeType.OPEN : PathNodeType.BLOCKED;
		}
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