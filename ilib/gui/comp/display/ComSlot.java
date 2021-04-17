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

package ilib.gui.comp.display;

import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/13 12:42
 */
public class ComSlot extends BaseComponent {
    protected Slot heldSlot;
    protected int shownX, shownY;
    protected int width, height;
    protected ComTexture slotTexture;
    public boolean display = true;

    /**
     * Creates an object that will move the physical slot when should render
     * <p>
     * This object will move the container slot, but also needs the texture to render
     *
     * @param parentGui The parent gui
     * @param heldSlot  The slot to move about
     * @param slotX     Slot x
     * @param slotY     Slot y
     * @param x         The component x
     * @param y         The component y
     */
    public ComSlot(IGui parentGui, Slot heldSlot, int slotX, int slotY, int x, int y, int u, int v, int w, int h) {
        super(parentGui, x, y);
        this.heldSlot = heldSlot;
        this.shownX = slotX;
        this.shownY = slotY;
        this.width = w;
        this.height = h;
        slotTexture = new ComTexture(owner, x - 1, y - 1, u, v, w, h);
    }

    public ComSlot(IGui parentGui, Slot heldSlot, int slotX, int slotY, int x, int y, int u, int v) {
        this(parentGui, heldSlot, slotX, slotY, x, y, u, v, 18, 18);
    }

    /**
     * Called by parent tab to move around
     *
     * @param doRender Do the render
     */
    public void display(boolean doRender) {
        this.display = doRender;
        if (doRender) {
            heldSlot.xPos = shownX;
            heldSlot.yPos = shownY;
        } else {
            heldSlot.xPos = -10000;
            heldSlot.yPos = -10000;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseComponent> T setTexture(ResourceLocation loc) {
        this.slotTexture.setTexture(loc);
        return (T) this;
    }

    /**
     * Called to render the component
     */
    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (display) {
            slotTexture.render(guiLeft, guiTop, mouseX, mouseY);
        }
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        // No op
    }

    /**
     * Used to find how wide this is
     *
     * @return How wide the component is
     */
    @Override
    public int getWidth() {
        return width;
    }

    /**
     * Used to find how tall this is
     *
     * @return How tall the component is
     */
    @Override
    public int getHeight() {
        return height;
    }
}
