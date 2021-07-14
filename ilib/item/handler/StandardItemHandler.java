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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: StandardItemHandler.java
 */
package ilib.item.handler;

import ilib.item.handler.inv.MIInventory;
import ilib.util.InventoryUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StandardItemHandler implements IItemHandlerModifiable, IItemHandler, ICapabilityProvider {
    protected MIInventory inv;

    public StandardItemHandler(MIInventory inv) {
        this.inv = inv;
    }

    private final List<InvChangeListener> callbacks = new ArrayList<>();

    public StandardItemHandler setInventory(MIInventory inv) {
        this.inv = inv;
        return this;
    }

    public MIInventory getInventory() {
        return this.inv;
    }

    public IItemHandler addCallback(InvChangeListener listener) {
        callbacks.add(listener);
        return this;
    }

    protected void onInventoryChanged(int slotId, ItemStack old, ItemStack now) {
        Iterator<InvChangeListener> it = callbacks.iterator();
        while (it.hasNext()) {
            InvChangeListener l = it.next();
            l.onInventoryChanged(this, slotId, old, now);
            if (l.shouldDelete())
                it.remove();
        }
    }

    public int getSlots() {
        return inv.size();
    }

    public void clear() {
        inv.clear();
    }

    protected boolean isValidSlot(int slotId) {
        return slotId >= 0 || slotId < getSlots();
    }

    public boolean isItemValid(int slotId, @Nonnull ItemStack stack) {
        return isValidSlot(slotId) && inv.isItemValid(slotId, stack);
    }

    public boolean isEmpty() {
        for (int i = inv.size() - 1; i >= 0; i--) {
            ItemStack is = inv.get(i);
            if (!is.isEmpty()) return false;
        }
        return true;
    }

    @Nonnull
    public ItemStack getStackInSlot(int slotId) {
        if (!isValidSlot(slotId)) return ItemStack.EMPTY;
        return inv.get(slotId);
    }

    public void setStackInSlot(int slotId, @Nonnull ItemStack stack) {
        if (!isValidSlot(slotId)) return;
        ItemStack old = inv.get(slotId);
        inv.set(slotId, stack);
        onInventoryChanged(slotId, old, stack);
    }

    @Nonnull
    public ItemStack insertItem(int slotId, @Nonnull ItemStack stack, boolean simulate) {
        if (!isValidSlot(slotId) || !inv.isItemValid(slotId, stack) || !inv.canInsert(slotId, stack)) return stack;
        if (stack.isEmpty())
            return ItemStack.EMPTY;
        ItemStack originalStack = getStackInSlot(slotId);
        if (originalStack.isEmpty()) {
            if (stack.getCount() > getSlotLimit(slotId)) {
                ItemStack copy = stack.copy();
                ItemStack target = copy.splitStack(getSlotLimit(slotId));
                if (!simulate) {
                    setStackInSlot(slotId, target);
                }
                return copy;
            }
            if (!simulate) {
                setStackInSlot(slotId, stack);
            }
            return ItemStack.EMPTY;
        } else if (InventoryUtil.areItemStacksEqual(originalStack, stack)) {
            int finalCount = stack.getCount() + originalStack.getCount();

            int max = Math.min(getSlotLimit(slotId), stack.getMaxStackSize());

            if (finalCount > max) {
                ItemStack copy = stack.copy();
                ItemStack target = copy.splitStack(finalCount - max);
                if (!simulate) {
                    copy = originalStack.copy();
                    copy.setCount(max);
                    setStackInSlot(slotId, copy);
                }
                return target;
            } else {
                if (!simulate) {
                    ItemStack copy = originalStack.copy();
                    copy.setCount(finalCount);
                    setStackInSlot(slotId, copy);
                }
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }

    @Nonnull
    public ItemStack extractItem(int slotId, int count, boolean simulate) {
        if (count == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = getStackInSlot(slotId);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!inv.canExtract(slotId, stack)) return ItemStack.EMPTY;

        ItemStack removed;

        if (stack.getCount() <= count) {
            removed = stack.copy();
            if (!simulate) {
                setStackInSlot(slotId, ItemStack.EMPTY);
            }
        } else {
            ItemStack backup = stack.copy();
            removed = backup.splitStack(count);
            if (!simulate) {
                setStackInSlot(slotId, backup.isEmpty() ? ItemStack.EMPTY : backup);
            }
        }

        return removed;
    }

    public int getSlotLimit(int slotId) {
        if (!isValidSlot(slotId)) return 0;
        return inv.slotLimit(slotId);
    }

    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            return (T) this;
        else
            return null;
    }
}
