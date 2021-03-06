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
package ilib.gui.slot;

import ilib.api.IItemFilter;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since  2021/1/13 13:03
 */
public class SlotFiltered extends SlotFlagged {
    private final IItemFilter valid;
    private final int id;

    /**
     * Customized slot: auto check can input / output
     *
     * @param inv   The tile entity
     * @param index The slot index
     * @param x     The x offset
     * @param y     The y offset
     */
    public <E extends IInventory & IItemFilter> SlotFiltered(E inv, int index, int x, int y, int id) {
        super(inv, index, x, y);
        this.valid = inv;
        this.id = id;
    }

    /**
     * Customized slot: auto check can input / output
     *
     * @param inv   The tile entity
     * @param index The slot index
     * @param valid The stack verifier
     * @param x     The x offset
     * @param y     The y offset
     */
    public SlotFiltered(IInventory inv, int index, int x, int y, IItemFilter valid, int id) {
        super(inv, index, x, y);
        this.valid = valid;
        this.id = id;
    }

    @Override
    public boolean isItemValid(@Nonnull ItemStack stack) {
        return valid.isItemValid(id, stack);
    }
}
