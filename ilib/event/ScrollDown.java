package ilib.event;

import ilib.ClientProxy;
import ilib.util.EntityHelper;
import roj.collect.MyHashMap;
import roj.math.Vec3d;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Iterator;

/**
 * @author Roj233
 * @since 2022/4/27 19:18
 */
public class ScrollDown {
	private static final MyHashMap<EntityPlayer, ScrollDown> active = new MyHashMap<>();

	private static final float SPIN_SPEED = 0.05f;

	private EntityPlayer owner;
	private int height;
	private BlockPos pos;
	private float boxSize2;

	private static ScrollDown check(EntityPlayer p) {
		World world = p.world;
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

		for (EnumFacing side : EnumFacing.HORIZONTALS) {
			pos.setPos(p).move(side);
			int y = pos.getY();

			AxisAlignedBB prevBB = null, bb;
			while (isValidBlock(bb = world.getBlockState(pos).getCollisionBoundingBox(world, pos))) {
				pos.setY(pos.getY() - 1);
				prevBB = bb;
			}

			if (y - pos.getY() > 1) {
				ScrollDown inst = new ScrollDown();
				inst.owner = p;
				inst.pos = pos.up();
				inst.boxSize2 = (float) (prevBB.maxX - prevBB.minX);

				int y1 = pos.getY();
				pos.setY(y);
				while (isValidBlock(world.getBlockState(pos).getCollisionBoundingBox(world, pos))) {
					pos.setY(pos.getY() + 1);
				}

				inst.height = pos.getY() - y1 - 1;
				pos.release();
				return inst;
			}
		}
		pos.release();
		return null;
	}

	private static boolean isValidBlock(AxisAlignedBB box) {
		if (box == null) return false;
		double dx = box.maxX - box.minX;
		double dy = box.maxY - box.minY;
		double dz = box.maxZ - box.minZ;
		return Math.abs(dx - dz) <= 0.01 && dy > 0.9 && dx < 0.3;
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) return;
		EntityPlayerSP clientPlayer = ClientProxy.mc.player;
		if (clientPlayer == null || !event.player.getUniqueID().equals(clientPlayer.getUniqueID())) return;

		ScrollDown inst = active.get(event.player);
		if (inst == null) {
			inst = check(event.player);
			if (inst != null) {
				active.put(event.player, inst);
			}
		}
		if (inst != null) {
			if (inst.tick()) active.remove(event.player);
		}
	}

	@SubscribeEvent
	@SideOnly(Side.SERVER)
	public static void onPlayerTick2(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) return;

		ScrollDown inst = active.get(event.player);
		if (inst == null) {
			inst = check(event.player);
			if (inst != null) {
				active.put(event.player, inst);
			}
		}
		if (inst != null) {
			if (inst.tick()) active.remove(event.player);
		}
	}

	private static int cleanupTimer;

	@SubscribeEvent
	@SideOnly(Side.SERVER)
	public static void cleanup(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) return;

		if (++cleanupTimer > 200) {
			cleanupTimer = 0;
			for (Iterator<EntityPlayer> itr = active.keySet().iterator(); itr.hasNext(); ) {
				EntityPlayerMP player = (EntityPlayerMP) itr.next();
				if (player.hasDisconnected()) itr.remove();
			}
		}
	}

	public static void cleanup() {
		active.clear();
	}

	private boolean tick() {
		EntityPlayer p = owner;
		if (p.onGround || p.posY < pos.getY() || p.posY > pos.getY() + height + 0.5) {
			return true;
		}

		if (pos.distanceSq(p.posX - 0.5, pos.getY(), p.posZ - 0.5) > boxSize2) return true;

		Vec3d center = new Vec3d(pos.getX() + 0.5, p.posY, pos.getZ() + 0.5);
		Vec3d pos = (Vec3d) EntityHelper.vec(p).sub(center).normalize();

		Vec3d spin = (Vec3d) new Vec3d(0, 1, 0).cross(pos).mul(SPIN_SPEED);

		p.motionY += (p.rotationPitch / 90) / 8f + 0.04f;
		p.motionY *= 0.8f;
		p.fallDistance = 1;

		float radius = p.width;
		Vec3d dest = ((Vec3d) center.add(pos.mul(radius)).add(spin)).add(0, p.motionY, 0);

		float ry = p.rotationYaw;
		p.rotationYaw = 180 - (float) (MathHelper.atan2(pos.x, pos.z) * (180 / Math.PI));
		p.prevRotationYaw = ry;

		if (!p.world.isRemote) {
			p.posX = dest.x;
			p.posY = dest.y;
			p.posZ = dest.z;
		} else {
			double x = p.posX;
			double y = p.posY;
			double z = p.posZ;
			p.setPositionAndUpdate(dest.x, dest.y, dest.z);
			p.lastTickPosX = p.prevPosX = x;
			p.lastTickPosY = p.prevPosY = y;
			p.lastTickPosZ = p.prevPosZ = z;
		}

		return false;
	}
}
