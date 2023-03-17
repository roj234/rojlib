package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * @author Roj234
 * @since 2020/10/3 1:03
 */
@Nixim("/")
abstract class NxRelAABB extends Entity {
	NxRelAABB(World worldIn) {
		super(worldIn);
	}

	@Copy
	protected static void writeAABB(Entity entity, NBTTagCompound compound) {
		NBTTagList list = new NBTTagList();
		AxisAlignedBB aabb = entity.getEntityBoundingBox();
		list.appendTag(new NBTTagDouble(aabb.minX));
		list.appendTag(new NBTTagDouble(aabb.minY));
		list.appendTag(new NBTTagDouble(aabb.minZ));
		list.appendTag(new NBTTagDouble(aabb.maxX));
		list.appendTag(new NBTTagDouble(aabb.maxY));
		list.appendTag(new NBTTagDouble(aabb.maxZ));
		compound.setTag("AABB", list);
	}

	@Copy
	protected static void readAABB(Entity entity, NBTTagCompound compound) {
		if (!compound.hasKey("AABB")) return;
		NBTTagList aabb = compound.getTagList("AABB", 6);
		entity.setEntityBoundingBox(new AxisAlignedBB(aabb.getDoubleAt(0), aabb.getDoubleAt(1), aabb.getDoubleAt(2), aabb.getDoubleAt(3), aabb.getDoubleAt(4), aabb.getDoubleAt(5)));
	}

	@Inject(value = "/", at = Inject.At.HEAD)
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		writeAABB(this, tag);
		return $$$CONTINUE_NBT();
	}

	private static NBTTagCompound $$$CONTINUE_NBT() {
		return null;
	}

	@Inject(value = "/", at = Inject.At.TAIL)
	public void readFromNBT(NBTTagCompound tag) {
		readAABB(this, tag);
	}
}
