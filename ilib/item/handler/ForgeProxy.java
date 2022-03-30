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

import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;

public class ForgeProxy extends SimpleInventory {
    private final IItemHandlerModifiable inv;

    public ForgeProxy(IItemHandlerModifiable inv) {
        super("ILProxiedForgeInv");
        this.inv = inv;
    }

    @Override
    public int getSlotLimit(int id) {
        return id < 0 ? 64 : inv.getSlotLimit(id);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return inv.isItemValid(slot, stack);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int id, int count, boolean simulate) {
        return inv.extractItem(id, count, simulate);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int id, @Nonnull ItemStack stack, boolean simulate) {
        return inv.insertItem(id, stack, simulate);
    }

    @Override
    public void setStackInSlot(int i, @Nonnull ItemStack stack) {
        inv.setStackInSlot(i, stack);
    }

    @Override
    public int getSlots() {
        return inv.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int i) {
        return inv.getStackInSlot(i);
    }
}
