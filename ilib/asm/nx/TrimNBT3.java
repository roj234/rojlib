package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/**
 * @author Roj234
 * @since 2020/10/3 1:03
 */
@Nixim("/")
abstract class TrimNBT3 extends EntityLiving {
	@Shadow
	private NonNullList<ItemStack> inventoryHands;
	@Shadow
	private NonNullList<ItemStack> inventoryArmor;
	@Shadow
	private boolean persistenceRequired;
	@Shadow
	private ResourceLocation deathLootTable;
	@Shadow
	private long deathLootTableSeed;
	@Shadow
	private boolean isLeashed;
	@Shadow
	private Entity leashHolder;

	TrimNBT3() {
		super(null);
	}

	@Inject("/")
	public void writeEntityToNBT(NBTTagCompound tag) {
		superCallHook(tag);
		tag.setBoolean("CanPickUpLoot", this.canPickUpLoot());
		if (persistenceRequired) tag.setBoolean("PersistenceRequired", true);

		NBTTagList list = new NBTTagList();
		boolean empty = true;
		for (int i = 0; i < inventoryArmor.size(); i++) {
			ItemStack stack = inventoryArmor.get(i);
			NBTTagCompound stackTag = new NBTTagCompound();
			if (!stack.isEmpty()) {
				stack.writeToNBT(stackTag);
				empty = false;
			}
			list.appendTag(stackTag);
		}
		if (!empty) tag.setTag("ArmorItems", list);

		list = new NBTTagList();
		empty = true;
		for (int i = 0; i < inventoryHands.size(); i++) {
			ItemStack stack = inventoryHands.get(i);
			NBTTagCompound stackTag = new NBTTagCompound();
			if (!stack.isEmpty()) {
				stack.writeToNBT(stackTag);
				empty = false;
			}
			list.appendTag(stackTag);
		}
		if (!empty) tag.setTag("HandItems", list);

		list = new NBTTagList();
		empty = true;
		for (float v : inventoryArmorDropChances) {
			list.appendTag(new NBTTagFloat(v));
			if (v != 0) empty = false;
		}
		if (!empty) tag.setTag("ArmorDropChances", list);

		list = new NBTTagList();
		empty = true;
		for (float v : inventoryHandsDropChances) {
			list.appendTag(new NBTTagFloat(v));
			if (v != 0) empty = false;
		}
		if (!empty) tag.setTag("HandDropChances", list);

		if (isLeashed) tag.setBoolean("Leashed", true);

		t:
		if (leashHolder != null) {
			NBTTagCompound tag1 = new NBTTagCompound();
			if (leashHolder instanceof EntityLivingBase) {
				tag1.setUniqueId("UUID", leashHolder.getUniqueID());
			} else if (leashHolder instanceof EntityHanging) {
				BlockPos pos = ((EntityHanging) leashHolder).getHangingPosition();
				tag1.setInteger("X", pos.getX());
				tag1.setInteger("Y", pos.getY());
				tag1.setInteger("Z", pos.getZ());
			} else {
				break t;
			}

			tag.setTag("Leash", tag1);
		}

		if (isLeftHanded()) tag.setBoolean("LeftHanded", true);

		if (deathLootTable != null) {
			tag.setString("DeathLootTable", deathLootTable.toString());
			if (deathLootTableSeed != 0L) {
				tag.setLong("DeathLootTableSeed", deathLootTableSeed);
			}
		}

		if (isAIDisabled()) tag.setBoolean("NoAI", true);
	}

	@Shadow(value = "func_70014_b", owner = "net.minecraft.entity.EntityLivingBase")
	private void superCallHook(NBTTagCompound tag) {}
}
