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

import ilib.ClientProxy;
import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.gui.comp.NinePatchRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ComReverseTab extends ComTab {

    /**
     * Creates a Gui Tab
     * <p>
     * IMPORTANT: Texture should be a full nine patchClient renderer minus the right column of cells
     * See NinePatchRenderer construction for more info
     */
    public ComReverseTab(IGui parent, int x, int y, int u, int v, int exWidth, int exHeight, @Nullable ItemStack displayStack) {
        super(parent, x, y, u, v, exWidth, exHeight, displayStack);
        tabRenderer = new NinePatchRenderer(u, v, 8, parent.getTexture());
    }

    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();

        // Set targets to stun
        int targetWidth = isActive ? expandedWidth : FOLDED_SIZE;
        int targetHeight = isActive ? expandedHeight : FOLDED_SIZE;

        // Move size
        if (currentWidth != targetWidth)
            currentWidth = targetWidth;
        if (currentHeight != targetHeight)
            currentHeight = targetHeight;

        // Render the tab
        tabRenderer.render(this, -currentWidth, 0, currentWidth, currentHeight);

        // Render the stack, if available
        RenderUtils.restoreColor();
        if (stack != null) {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            // -21 3
            ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, -20, 4);
            RenderUtils.restoreColor();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_LIGHTING);
        }

        // Render the children
        if (areChildrenActive()) {
            GlStateManager.translate(-expandedWidth, 0, 0);
            for (BaseComponent component : children) {
                RenderUtils.prepareRenderState();
                component.render(-expandedWidth, 0, mouseX, mouseY);
                RenderUtils.restoreColor();
                RenderUtils.restoreRenderState();
            }
        }

        GlStateManager.popMatrix();
    }

    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        // Render the children
        if (areChildrenActive()) {
            GlStateManager.translate(-expandedWidth, 0, 0);
            for (BaseComponent component : children) {
                RenderUtils.prepareRenderState();
                component.renderOverlay(-expandedWidth, 0, mouseX, mouseY);
                RenderUtils.restoreColor();
                RenderUtils.restoreRenderState();
            }
        }
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= xPos - currentWidth && mouseX < xPos && mouseY >= yPos && mouseY < yPos + getHeight();
    }
}
