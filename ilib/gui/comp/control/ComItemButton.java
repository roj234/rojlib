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

package ilib.gui.comp.control;

import ilib.ClientProxy;
import ilib.gui.IGui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class ComItemButton extends ComButtonOver {
    // Variables
    protected ItemStack stack;

    /**
     * Constructor for itemstack button
     *
     * @param stack The stack to display
     */
    public ComItemButton(IGui parent, int x, int y, int u, int v, int w, int h, ItemStack stack) {
        super(parent, x, y, u, v, w, h, null);
        this.stack = stack;
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        super.renderOverlay(guiLeft, guiTop, mouseX, mouseY);

        RenderHelper.enableGUIStandardItemLighting();

        ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, xPos + (width / 2) - 8, yPos + (height / 2) - 8);

        RenderHelper.disableStandardItemLighting();
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }
}
