package ilib.item.handler;

import ilib.api.IItemFilter;
import ilib.util.NBTType;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;

public abstract class ItemInventory extends ListInventory implements ICapabilitySerializable<NBTTagCompound> {
	private final ItemStack heldStack;
	private IItemFilter verifier;

	public ItemInventory(ItemStack stack, NBTTagCompound nbt, int size) {
		super(size);
		heldStack = stack;
		if (nbt != null) deserializeNBT(nbt);
	}

	protected boolean checkStackNBT() {
		return verifier == null || verifier.isItemValid(-1, heldStack);
	}

	public void setVerifier(IItemFilter verifier) {
		if (this.verifier != null && verifier != null) throw new IllegalStateException("2 verifiers");
		this.verifier = verifier;
	}

	public NBTTagCompound serializeNBT() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setTag("Items", writeToNBT());
		return tag;
	}

	public void deserializeNBT(NBTTagCompound tag) {
		readFromNBT(tag.getTagList("Items", NBTType.COMPOUND));
	}

	@Override
	public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
		if (!checkStackNBT()) {
			stack = ItemStack.EMPTY;
		}
		super.setStackInSlot(slot, stack);
	}
}