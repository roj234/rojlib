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

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.gui.DefaultSprites;
import ilib.gui.GuiHelper;
import ilib.gui.IGui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class GReverseTab extends GTab {
    private int prevX;

    public GReverseTab(IGui parent, int x, int y, int exWidth, int exHeight, @Nullable ItemStack stack) {
        super(parent, x, y, exWidth, exHeight, stack);
        prevX = x;
    }

    GReverseTab(IGui parent, int x, int y) {
        super(parent, x, y);
        prevX = x;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        RenderUtils.setColor(color);
        DefaultSprites.TAB.render(xPos, yPos, 0, 9, width, height);

        if (stack != null) {
            RenderHelper.enableGUIStandardItemLighting();

            ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI(stack,
                                           xPos + width - 20, yPos + 4);

            RenderHelper.disableStandardItemLighting();
        }

        if (isActive()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(xPos, yPos, 0);
            GuiHelper.renderBackground(mouseX - xPos, mouseY - yPos, components);
            GlStateManager.popMatrix();
        }

        animateFold();
        xPos = prevX - width + FOLDED_SIZE;
    }

    @Override
    public void setXPos(int xPos) {
        prevX = xPos;
    }
}
