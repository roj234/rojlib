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

package ilib.gui.comp;

import ilib.gui.IGui;
import ilib.gui.util.Sprite;

import net.minecraft.inventory.Slot;

/**
 * @author Roj234
 * @since 2021/1/13 12:42
 */
public class GSlot extends GTexture {
    protected Slot slot;
    protected int shownX, shownY;
    protected boolean display = true;

    public GSlot(IGui parent, Slot slot, int slotX, int slotY, int x, int y, int u, int v, int w, int h) {
        super(parent, x, y, u, v, w, h);
        this.slot = slot;
        this.shownX = slotX;
        this.shownY = slotY;
    }

    public GSlot(IGui parent, Slot slot, int slotX, int slotY, int x, int y, Sprite bg) {
        super(parent, x - (bg.w() - 16) / 2, y - (bg.h() - 16) / 2, bg);
        this.slot = slot;
        this.shownX = slotX;
        this.shownY = slotY;
    }

    @Deprecated
    public GSlot(IGui parent, Slot slot, int x, int y, int u, int v) {
        this(parent, slot, x + 1, y + 1, x, y, u, v, 18, 18);
    }

    public void setVisible(boolean display) {
        this.display = display;
        if (display) {
            slot.xPos = shownX;
            slot.yPos = shownY;
        } else {
            slot.xPos = -10000;
            slot.yPos = -10000;
        }
    }

    @Override
    protected boolean isVisible() {
        return display;
    }

    public Slot getSlot() {
        return slot;
    }
}
