package ilib.api.recipe;

import ilib.fluid.handler.IFluidProvider;
import ilib.util.InventoryUtil;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public interface IRecipe {
	boolean isShaped();

	default boolean isStandard() {
		return false;
	}

	default boolean isRandomized() {
		return false;
	}

	default int getCount(int id, ItemStack stack) {
		return stack.getCount();
	}

	@Deprecated
	default int getMin(int id) {
		throw new UnsupportedOperationException();
	}

	boolean matches(@Nullable IFluidProvider fp, List<ItemStack> list);

	List<ItemStack> operateInput(@Nullable IFluidProvider fp, List<ItemStack> input);

	String getName();

	int getTimeCost();

	int getPowerCost();

	default boolean willConsume(int index) {
		return true;
	}

	List<ItemStack> getInput();

	List<ItemStack> getOutput();

	default List<ItemStack> getOutput(List<ItemStack> inputs) {
		return getOutput();
	}

	static boolean stackEquals(ItemStack self, @Nonnull ItemStack rec) {
		return InventoryUtil.areItemStacksEqual(self, rec) || (rec.hasTagCompound() && rec.getTagCompound().hasKey("_MI_ANYITEM"));
	}

	static ItemStack decrStackSize(@Nonnull List<ItemStack> list, int slotIndex, int count) {
		ItemStack stack = list.get(slotIndex);

		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack removed;

		if (stack.getCount() <= count) {
			removed = stack.copy();
			list.set(slotIndex, ItemStack.EMPTY);
		} else {
			removed = stack.splitStack(count);
			if (stack.getCount() == 0) {
				list.set(slotIndex, ItemStack.EMPTY);
			}
		}

		return removed;
	}

	@Nonnull
	default List<FluidStack> getJEIFluidInput() {
		return Collections.emptyList();
	}

	@Nonnull
	default List<FluidStack> getJEIFluidOutput() {
		return Collections.emptyList();
	}

	@Nonnull
	default List<ItemStack> getJEIInput() {
		return getInput();
	}

	@Nonnull
	default List<ItemStack> getJEIOutput() {
		return getOutput();
	}
}