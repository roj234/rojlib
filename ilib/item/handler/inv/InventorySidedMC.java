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
 * Filename: InventorySidedMC.java
 */
package ilib.item.handler.inv;

import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public class InventorySidedMC implements MIInventorySided {
    protected final ISidedInventory inv;
    protected EnumFacing face;

    public InventorySidedMC(ISidedInventory inv, @Nullable EnumFacing face) {
        this.inv = inv;
        this.face = face;
    }

    public InventorySidedMC setFace(@Nullable EnumFacing face) {
        this.face = face;
        return this;
    }

    public void clear() {
        if (face == null) {
            inv.clear();
            return;
        }
        int[] slotIds = inv.getSlotsForFace(face);
        for (int id : slotIds) {
            inv.setInventorySlotContents(id, ItemStack.EMPTY);
        }
    }

    public ItemStack get(int slotId) {
        slotId = getRealSlot(slotId);
        if (slotId == -1) return ItemStack.EMPTY;
        return inv.getStackInSlot(slotId);
    }

    public boolean isItemValid(int slotId, ItemStack stack) {
        slotId = getRealSlot(slotId);
        if (slotId == -1) return false;
        return inv.isItemValidForSlot(slotId, stack);
    }

    public void set(int slotId, ItemStack stack) {
        slotId = getRealSlot(slotId);
        if (slotId == -1) return;
        inv.setInventorySlotContents(slotId, stack);
    }

    public int size() {
        return face == null ? inv.getSizeInventory() : inv.getSlotsForFace(face).length;
    }

    public int slotLimit(int slotId) {
        return inv.getInventoryStackLimit();
    }

    public Object getInvHolder() {
        return inv;
    }

    public boolean canInsert(int slotId, ItemStack stack) {
        return face == null || inv.canInsertItem(getRealSlot(slotId), stack, face);
    }

    public boolean canExtract(int slotId, ItemStack stack) {
        return face == null || inv.canExtractItem(getRealSlot(slotId), stack, face);
    }

    @Override
    public int getRealSlot(int slot) {
        if (face == null) {
            if (slot < 0 || slot >= inv.getSizeInventory())
                return -1;
            return slot;
        } else {
            int[] slotIds = inv.getSlotsForFace(face);
            if (slot < 0 || slot >= slotIds.length)
                return -1;
            return slotIds[slot];
        }
    }
}
