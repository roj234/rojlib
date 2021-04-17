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
 * Filename: IItemHandler.java
 */
package ilib.item.handler;

import ilib.item.handler.inv.MIInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public interface IItemHandler {
    int getSlots();

    boolean isEmpty();

    void clear();

    ItemStack getStackInSlot(int slotId);

    void setStackInSlot(int slotId, ItemStack stack);

    boolean isItemValid(int slotId, ItemStack stack);

    ItemStack insertItem(int slotId, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slotId, int count, boolean simulate);

    IItemHandler addCallback(InvChangeListener listener);

    MIInventory getInventory();

    int getSlotLimit(int slotId);

    default void copyFrom(IItemHandler h) {
        getInventory().copyFrom(h.getInventory());
    }

    default NBTTagCompound writeToNBT(NBTTagCompound tag) {
        return getInventory().writeToNBT(tag);
    }

    default void readFromNBT(NBTTagCompound tag) {
        getInventory().readFromNBT(tag);
    }
}