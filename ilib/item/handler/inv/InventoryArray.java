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
 * Filename: InventoryArray.java
 */
package ilib.item.handler.inv;

import net.minecraft.item.ItemStack;
import roj.collect.ArrayIterator;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Iterator;

public class InventoryArray implements MIInventory, Iterable<ItemStack> {
    protected final ItemStack[] inv;
    protected final int max;

    public InventoryArray(int max, ItemStack def) {
        this.inv = new ItemStack[max];
        this.max = max;
        Arrays.fill(inv, def);
    }

    public Object getInvHolder() {
        return inv;
    }

    public void clear() {
        Arrays.fill(inv, ItemStack.EMPTY);
    }

    public ItemStack get(int slotId) {
        if (slotId < 0 || slotId > max)
            return ItemStack.EMPTY;
        return inv[slotId];
    }

    public boolean isItemValid(int slotId, ItemStack stack) {
        return true;
    }

    public void set(int slotId, ItemStack stack) {
        if (slotId < 0 || slotId > max)
            return;
        inv[slotId] = stack;
    }

    public int size() {
        return max;
    }

    public int slotLimit(int slotId) {
        return 64;
    }

    @Override
    public String toString() {
        return "InventoryArray{" + Arrays.toString(inv) + '}';
    }

    @Nonnull
    @Override
    public Iterator<ItemStack> iterator() {
        return new ArrayIterator<>(inv);
    }
}
