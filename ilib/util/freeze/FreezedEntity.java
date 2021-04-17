package ilib.util.freeze;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;

/**
 * 冻结的实体
 *
 * @author Roj233
 * @since 2021/8/26 19:36
 */
public final class FreezedEntity extends Entity {
	public NBTTagCompound entityTag;

	public FreezedEntity(World worldIn) {
		super(worldIn);
	}

	@Override
	protected void entityInit() {}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		try {
			NBTTagList poses = compound.getTagList("Pos", 6);
			this.posX = poses.getDoubleAt(0);
			this.posY = poses.getDoubleAt(1);
			this.posZ = poses.getDoubleAt(2);
			this.lastTickPosX = this.posX;
			this.lastTickPosY = this.posY;
			this.lastTickPosZ = this.posZ;
			this.prevPosX = this.posX;
			this.prevPosY = this.posY;
			this.prevPosZ = this.posZ;

			NBTTagList rotations = compound.getTagList("Rotation", 5);
			this.rotationYaw = rotations.getFloatAt(0);
			this.rotationPitch = rotations.getFloatAt(1);
			this.prevRotationYaw = this.rotationYaw;
			this.prevRotationPitch = this.rotationPitch;
			this.setRotationYawHead(this.rotationYaw);
			this.setRenderYawOffset(this.rotationYaw);

			if (compound.hasKey("Dimension")) {
				this.dimension = compound.getInteger("Dimension");
			}

			if (compound.hasUniqueId("UUID")) {
				this.entityUniqueID = compound.getUniqueId("UUID");
				this.cachedUniqueIdString = this.entityUniqueID.toString();
			}

			this.setEntityInvulnerable(true);
			this.setPosition(this.posX, this.posY, this.posZ);
			this.setRotation(this.rotationYaw, this.rotationPitch);

			this.setCustomNameTag("冻结的实体 " + compound.getString("id") + " == " + this.cachedUniqueIdString);
			this.setAlwaysRenderNameTag(true);
			this.setNoGravity(true);
			this.setGlowing(true);
			this.updateBlocked = true;
			this.entityTag = compound.copy();
		} catch (Throwable var8) {
			new RuntimeException("无法加载实体NBT数据 " + compound.getString("id"), var8).printStackTrace();
		}
	}

	@Override
	public void setDead() {}
	@Override
	public void onKillCommand() {}

	@Override
	public void onEntityUpdate() {
		this.firstUpdate = false;
	}

	@Override
	public boolean writeToNBTAtomically(NBTTagCompound tag) {
		return writeToNBTOptional(tag);
	}

	@Override
	public boolean writeToNBTOptional(NBTTagCompound tag) {
		String id = getEntityString();
		if (id != null) {
			tag.setString("id", id);
			writeToNBT(tag);
			return true;
		}
		return false;
	}

	@Nullable
	@Override
	public Entity changeDimension(int dimensionIn, ITeleporter teleporter) {
		return null;
	}

	@Override
	public void applyEntityCollision(Entity entityIn) {}

	@Override
	public EnumActionResult applyPlayerInteraction(EntityPlayer player, Vec3d vec, EnumHand hand) {
		return EnumActionResult.FAIL;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		if (entityTag != null) tag.merge(entityTag);
		return tag;
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound tag) {}
	@Override
	protected void writeEntityToNBT(NBTTagCompound tag) {}
}
