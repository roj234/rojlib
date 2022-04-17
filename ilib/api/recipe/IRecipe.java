/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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