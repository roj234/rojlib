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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * @author Roj234
 * @since  2021/1/13 13:03
 */
public class SlotFlagged extends Slot {
    public static int UPDATE = 1, NO_TAKE = 2, NO_PUT = 4, NO_DISPLAY = 8;

    public static SlotFlagged DISABLED_SLOT = new SlotFlagged(null, 0, 0, 0, NO_DISPLAY);

    protected int flag;

    public SlotFlagged(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    public SlotFlagged(IInventory inv, int index, int x, int y, int flag) {
        super(inv, index, x, y);
        this.flag = flag;
    }

    public void onSlotChanged() {
        if ((flag & UPDATE) != 0)
            inventory.markDirty();
    }

    @Override
    public boolean isEnabled() {
        return (flag & NO_DISPLAY) == 0;
    }

    @Override
    public boolean canTakeStack(EntityPlayer playerIn) {
        return (flag & NO_TAKE) == 0;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return (flag & NO_PUT) == 0;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }
}
