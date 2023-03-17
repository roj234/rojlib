package ilib.item.handler;

import ilib.api.IItemFilter;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/15 10:41
 */
public class ListInventory extends SimpleInventory {
	protected ItemStack[] inv;
	protected boolean dynamic, dirty;
	protected final List<InvChangeListener> callbacks = new ArrayList<>();
	protected IItemFilter filter;

	public ListInventory(int max) {
		super("ListInventory");
		this.inv = new ItemStack[max];
		clear();
	}

	public ListInventory setDynamic(boolean dynamic) {
		this.dynamic = dynamic;
		return this;
	}

	public void setFilter(IItemFilter filter) {
		this.filter = filter;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void clearDirty() {
		dirty = false;
	}

	@Override
	public void markDirty() {
		dirty = true;
	}

	public ItemStack[] getInventory() {
		return inv;
	}

	public void setInventorySize(int size) {
		if (inv.length == size) return;
		ItemStack[] old = this.inv;
		this.inv = new ItemStack[size];
		clear();
		System.arraycopy(old, 0, inv, 0, Math.min(size, old.length));
		dirty = true;
	}

	@Override
	public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
		return filter == null || filter.isItemValid(slot, stack);
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : inv) {
			if (!stack.isEmpty()) return false;
		}
		return true;
	}

	@Override
	public void clear() {
		Arrays.fill(inv, ItemStack.EMPTY);
		dirty = true;
	}

	@Override
	public void setStackInSlot(int id, @Nonnull ItemStack stack) {
		ItemStack prev = inv[id];

		inv[id] = stack;
		dirty = true;

		inventoryChanged(id, prev, stack);
	}

	@Override
	public int getSlots() {
		return inv.length;
	}

	@Nonnull
	@Override
	public ItemStack getStackInSlot(int id) {
		return inv[id];
	}

	public ListInventory addCallback(InvChangeListener listener) {
		callbacks.add(listener);
		return this;
	}

	public void removeCallback(InvChangeListener listener) {
		callbacks.remove(listener);
	}

	protected void inventoryChanged(int slotId, ItemStack old, ItemStack now) {
		for (int i = 0; i < callbacks.size(); i++) {
			InvChangeListener l = callbacks.get(i);
			l.onInventoryChanged(this, slotId, old, now);
		}
	}

	public NBTTagList writeToNBT() {
		NBTTagList list = new NBTTagList();
		for (ItemStack stack : this.inv) {
			NBTTagCompound st = new NBTTagCompound();
			if (!stack.isEmpty()) {
				stack.writeToNBT(st);
			}
			list.appendTag(st);
		}
		return list;
	}

	public void readFromNBT(NBTTagList list) {
		if (dynamic && list.tagCount() != inv.length) {
			inv = new ItemStack[list.tagCount()];
		}
		int x = Math.min(list.tagCount(), inv.length);
		int i = 0;
		for (; i < x; i++) {
			NBTTagCompound st = list.getCompoundTagAt(i);
			if (st.isEmpty()) {
				inv[i] = ItemStack.EMPTY;
			} else {
				inv[i] = new ItemStack(st);
			}
		}
		while (i < inv.length) inv[i++] = ItemStack.EMPTY;
		dirty = true;
	}
}
