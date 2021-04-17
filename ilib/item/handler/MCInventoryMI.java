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
 * Filename: InventoryMC.java
 */
package ilib.item.handler;

import ilib.api.Syncable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

public class MCInventoryMI implements IInventory {
    private final IItemHandler inv;
    private final Syncable sync;

    public MCInventoryMI(IItemHandler inv, Syncable sync) {
        this.inv = inv;
        this.sync = sync;
    }

    public int getSizeInventory() {
        return inv.getSlots();
    }

    public boolean isEmpty() {
        return inv.isEmpty();
    }

    public boolean hasCustomName() {
        return false;
    }

    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    public String getName() {
        return "container.mi.proxied_inv.name";
    }

    public ItemStack getStackInSlot(int slotId) {
        return inv.getStackInSlot(slotId);
    }

    public ItemStack decrStackSize(int slotId, int count) {
        return inv.extractItem(slotId, count, false);
    }

    public ItemStack removeStackFromSlot(int slotId) {
        ItemStack stack = inv.getStackInSlot(slotId);
        inv.setStackInSlot(slotId, ItemStack.EMPTY);
        return stack;
    }

    public void setInventorySlotContents(int slotId, ItemStack stack) {
        inv.setStackInSlot(slotId, stack);
    }

    public int getInventoryStackLimit() {
        return 64;
    }

    public void markDirty() {
    }

    public boolean isUsableByPlayer(EntityPlayer p) {
        return true;
    }

    public void openInventory(EntityPlayer p) {
    }

    public void closeInventory(EntityPlayer p) {
    }

    public boolean isItemValidForSlot(int slotId, ItemStack stack) {
        return inv.isItemValid(slotId, stack);
    }

    public int getField(int id) {
        return sync == null ? 0 : sync.getServerOnlyField(id);
    }

    public void setField(int id, int val) {
        if (sync != null)
            sync.setServerOnlyField(id, val);
    }

    public int getFieldCount() {
        return sync == null ? 0 : sync.getServerOnlyFieldCount();
    }

    public void clear() {
        inv.clear();
    }
}
