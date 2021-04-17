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
 * Filename: MIInventory.java
 */
package ilib.item.handler.inv;

import ilib.ImpLib;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.NoSuchElementException;

public interface MIInventory extends Iterable<ItemStack> {
    ItemStack get(int slotId);

    boolean isItemValid(int slotId, ItemStack stack);

    void set(int slotId, ItemStack stack);

    int size();

    int slotLimit(int slotId);

    void clear();

    Object getInvHolder();

    @Nonnull
    @Override
    default Iterator<ItemStack> iterator() {
        return new ForeachIterator(this);
    }

    default boolean canInsert(int slotId, ItemStack stack) {
        return true;
    }

    default boolean canExtract(int slotId, ItemStack stack) {
        return true;
    }

    default boolean canCopy(MIInventory inv) {
        return inv.getClass() == this.getClass();
    }

    default NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList data = new NBTTagList();
        for (int i = 0; i < size(); i++) {
            ItemStack stack = get(i);
            if (stack != null && !stack.isEmpty()) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                stack.writeToNBT(t);
                data.appendTag(t);
            }
        }

        tag.setTag("Items", data);

        return tag;
    }

    default void readFromNBT(NBTTagCompound tag) {
        clear();

        final byte NBT_TYPE_COMPOUND = 10;
        NBTTagList data = tag.getTagList("Items", NBT_TYPE_COMPOUND);

        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound t = data.getCompoundTagAt(i);
            byte slotNumber = t.getByte("Slot");

            if (slotNumber >= 0 && slotNumber < size()) {
                set(slotNumber, new ItemStack(t));
            } else {
                ImpLib.logger().warn("[O]Invalid slot index " + slotNumber);
            }
        }
    }

    default void copyFrom(MIInventory inv) {
        if (!canCopy(inv)) return;
        for (int i = Math.min(size(), inv.size()) - 1; i >= 0; i--) {
            ItemStack stack = inv.get(i);
            if (!stack.isEmpty())
                set(i, stack.copy());
            else
                set(i, ItemStack.EMPTY);
        }
    }

    class ForeachIterator implements Iterator<ItemStack> {
        int slotId = 0;
        private final MIInventory inventory;

        protected ForeachIterator(MIInventory inventory) {
            this.inventory = inventory;
        }

        /**
         * Returns {@code true} if the iteration has more elements.
         * (In other words, returns {@code true} if {@link #next} would
         * return an element rather than throwing an exception.)
         *
         * @return {@code true} if the iteration has more elements
         */
        @Override
        public boolean hasNext() {
            return slotId < inventory.size();
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        public ItemStack next() {
            if (hasNext()) {
                return inventory.get(slotId++);
            } else {
                throw new NoSuchElementException();
            }
        }

        /**
         * Removes from the underlying collection the last element returned
         * by this iterator (optional operation).  This method can be called
         * only once per call to {@link #next}.  The behavior of an iterator
         * is unspecified if the underlying collection is modified while the
         * iteration is in progress in any way other than by calling this
         * method.
         *
         * @throws UnsupportedOperationException if the {@code remove}
         *                                       operation is not supported by this iterator
         * @throws IllegalStateException         if the {@code next} method has not
         *                                       yet been called, or the {@code remove} method has already
         *                                       been called after the last call to the {@code next}
         *                                       method
         * @implSpec The default implementation throws an instance of
         * {@link UnsupportedOperationException} and performs no other action.
         */
        @Override
        public void remove() {
            if (inventory.canInsert(slotId - 1, ItemStack.EMPTY) && inventory.isItemValid(slotId - 1, ItemStack.EMPTY)) {
                inventory.set(slotId - 1, ItemStack.EMPTY);
            } else
                throw new UnsupportedOperationException();
        }
    }
}
