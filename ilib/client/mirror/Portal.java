package ilib.client.mirror;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import static ilib.ClientProxy.mc;

public abstract class Portal {
	public WorldClient world;

	protected EnumFacing faceOn;
	protected AxisAlignedBB plane;
	protected BlockPos pos;
	protected Portal pair;

	public Portal(WorldClient world) {
		this.world = world;
		this.pos = BlockPos.ORIGIN;

		this.faceOn = EnumFacing.NORTH;
	}

	public Portal(WorldClient world, BlockPos position, EnumFacing faceOn) {
		this.world = world;
		this.pos = position;

		this.faceOn = faceOn;
	}

	public abstract void renderPlane(float partialTick);

	public EnumFacing getFaceOn() {
		return faceOn;
	}

	public BlockPos getPos() {
		return new BlockPos(plane.getCenter());
	}

	public WorldClient getWorld() {
		return world;
	}

	public AxisAlignedBB getPlane() {
		return plane;
	}

	public boolean getCullRender() {
		return true;
	}

	public boolean getOneSideRender() {
		return true;
	}

	public boolean renderNearerFog() {
		return false;
	}

	public boolean hasPair() {
		return pair != null;
	}

	public Portal getPair() {
		return pair;
	}

	public void setPair(Portal portal) {
		if (pair != portal) {
			pair = portal;
		}
	}

	public void setPosition(BlockPos v) {
		this.pos = v.toImmutable();
	}

	public int getRenderDistanceChunks() {
		return Math.min(mc.gameSettings.renderDistanceChunks, Mirror.maxRenderDistanceChunks);
	}
}
