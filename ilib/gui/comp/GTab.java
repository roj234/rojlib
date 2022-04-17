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
import java.awt.*;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GTab extends GGroup {
    protected static final int FOLDED_SIZE = 24;
    protected static final int FOLD_ANIMATE_DIVIDER = 5;

    protected ItemStack stack;
    protected Color color;

    protected int expandedWidth, expandedHeight;

    public GTab(IGui parent, int x, int y, int exWidth, int exHeight, @Nullable ItemStack label) {
        super(parent, x, y, FOLDED_SIZE, FOLDED_SIZE);
        this.expandedWidth = exWidth;
        this.expandedHeight = exHeight;
        this.stack = label;
        this.color = Color.RED;
        this.active = false;
    }

    GTab(IGui parent, int x, int y) {
        super(parent, x, y, FOLDED_SIZE, FOLDED_SIZE);
        this.active = false;
    }

    public final GTab addChild(Component com) {
        components.add(com);
        return this;
    }

    @Override
    public void onInit() {
        if (listener != null) listener.componentInit(this);

        for (int i = 0; i < components.size(); i++) {
            components.get(i).onInit();
        }
    }

    public final boolean mouseDownActivated(int x, int y, int button) {
        if (isActive()) {
            x -= xPos;
            y -= yPos;

            List<Component> clicked = this.clicked[button];
            for (int i = 0; i < components.size(); i++) {
                Component com = components.get(i);
                if (com.isMouseOver(x, y)) {
                    com.mouseDown(x, y, button);
                    clicked.add(com);
                }
            }
            return !clicked.isEmpty() || button != GuiHelper.LEFT;
        }
        return button != GuiHelper.LEFT;
    }

    protected final void animateFold() {
        int width = this.width;
        int height = this.height;

        if (width != (active ? expandedWidth : FOLDED_SIZE)) {
            int pw = Math.max((expandedWidth - FOLDED_SIZE) / FOLD_ANIMATE_DIVIDER, 1);
            int ph = Math.max((expandedHeight - FOLDED_SIZE) / FOLD_ANIMATE_DIVIDER, 1);
            if (active) {
                width += pw;
                height += ph;
                if (height >= expandedHeight || width >= expandedWidth) {
                    height = expandedHeight;
                    width = expandedWidth;
                }
            } else {
                width -= pw;
                height -= ph;
                if (height <= FOLDED_SIZE || width <= FOLDED_SIZE) {
                    height = width = FOLDED_SIZE;
                }
            }

            this.width = width;
            this.height = height;

            toggleSlot();
        }
    }

    /**
     * Called to render the component
     */
    @Override
    public void render(int mouseX, int mouseY) {
        RenderUtils.setColor(color);
        DefaultSprites.TAB.render(xPos, yPos, width, height);

        if (stack != null) {
            RenderHelper.enableGUIStandardItemLighting();

            ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI(stack,
                                          xPos + 4, yPos + 4);

            RenderHelper.disableStandardItemLighting();
        }

        if (isActive()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(xPos, yPos, 0);
            GuiHelper.renderBackground(mouseX - xPos, mouseY - yPos, components);
            GlStateManager.popMatrix();
        }

        animateFold();
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public final ItemStack getStack() {
        return stack;
    }

    public final void setStack(ItemStack stack) {
        this.stack = stack;
    }

    public final boolean isActive() {
        return active && width == expandedWidth && height == expandedHeight;
    }

    public Color getColor() {
        return color;
    }

    public GTab setColor(Color color) {
        this.color = color;
        return this;
    }
}
