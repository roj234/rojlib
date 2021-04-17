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
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ComTab extends BaseComponent {
    // Class Variables
    protected static final int FOLDED_SIZE = 24;

    // Variables
    protected int expandedWidth, expandedHeight,
            u, v;
    protected ItemStack stack;
    protected int currentWidth = FOLDED_SIZE,
            currentHeight = FOLDED_SIZE;
    protected boolean isActive = false;

    protected List<BaseComponent> children;

    protected NinePatchRenderer tabRenderer;

    /**
     * Creates a Gui Tab
     * <p>
     * IMPORTANT: Texture should be a full nine patchClient renderer minus the left column of cells
     * See NinePatchRenderer construction for more info
     */
    public ComTab(IGui parent, int x, int y, int u, int v, int exWidth, int exHeight, @Nullable ItemStack displayStack) {
        super(parent, x, y);
        this.u = u;
        this.v = v;
        this.expandedWidth = exWidth;
        this.expandedHeight = exHeight;
        this.stack = displayStack;

        children = new ArrayList<>();
        tabRenderer = new NinePatchRenderer(u, v, 8, parent.getTexture());
    }

    @SuppressWarnings("unchecked")
    public final <T extends BaseComponent> T setTexture(ResourceLocation loc) {
        this.tabRenderer.setTexture(loc);
        return (T) this;
    }

    /**
     * Add a child to this tab
     *
     * @param component The component to add
     * @return The tab, to enable chaining
     */
    public final ComTab addChild(BaseComponent component) {
        children.add(component);
        return this;
    }

    /**
     * Can the tab render the children
     *
     * @return True if expanded and can render
     */
    public final boolean areChildrenActive() {
        return isActive;// && currentWidth == expandedWidth && currentHeight == expandedHeight;
    }

    /**
     * Moves the slots if need be
     */
    public final void moveSlots() {
        for (Object component : children) {
            if (component instanceof ComSlot)
                ((ComSlot) component).display(areChildrenActive());
        }
    }

    public final boolean mouseDownActivated(int x, int y, int button) {
        if (this.guiListener != null)
            this.guiListener.onMouseDown(this, x, y, button);
        if (areChildrenActive()) {
            for (BaseComponent component : children) {
                if (component.isMouseOver(x, y)) {
                    component.mouseDown(x, y, button);
                    return true;
                }
            }
        }
        return false;
    }

    public final boolean mouseUpActivated(int x, int y, int button) {
        if (areChildrenActive()) {
            for (BaseComponent component : children) {
                if (component.isMouseOver(x, y)) {
                    component.mouseUp(x, y, button);
                    return true;
                }
            }
        }
        return false;
    }

    public final boolean mouseDragActivated(int x, int y, int button, long time) {
        if (areChildrenActive()) {
            for (BaseComponent component : children) {
                if (component.isMouseOver(x, y)) {
                    component.mouseDrag(x, y, button, time);
                    return true;
                }
            }
        }
        return false;
    }

    public final void mouseScrolledTab(int x, int y, int dir) {
        if(!isMouseOver(x, y))
            return;
        if (areChildrenActive()) {
            for (BaseComponent component : children) {
                component.mouseScrolled(x, y, dir);
            }
        }
    }

    /*******************************************************************************************************************
     * BaseComponent                                                                                                   *
     *******************************************************************************************************************/

    /**
     * Used when a key is pressed
     *
     * @param letter  The letter
     * @param keyCode The code
     */
    @Override
    public final void keyTyped(char letter, int keyCode) {
        for (BaseComponent component : children) {
            component.keyTyped(letter, keyCode);
        }
    }

    /**
     * Render the tooltip if you can
     *
     * @param mouseX Mouse X
     * @param mouseY Mouse Y
     */
    @Override
    public final void renderToolTip(int mouseX, int mouseY) {
        if (areChildrenActive()) {
            for (BaseComponent component : children) {
                if (component.isMouseOver(mouseX - xPos - owner.getGuiLeft(), mouseY - yPos - owner.getGuiTop()))
                    component.renderToolTip(mouseX, mouseY);
            }
        } else
            super.renderToolTip(mouseX, mouseY);
    }

    /**
     * Called to render the component
     */
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
        tabRenderer.render(this, 0, 0, currentWidth, currentHeight);

        // Render the stack, if available
        RenderUtils.restoreColor();
        if (stack != null) {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            // 4 3
            ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 3, 4);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            RenderUtils.restoreColor();
        }

        // Render the children
        if (areChildrenActive()) {
            for (BaseComponent component : children) {
                //RenderUtils.prepareRenderState();
                component.render(0, 0, mouseX, mouseY);
                RenderUtils.restoreColor();
                //RenderUtils.restoreRenderState();
            }
        }

        GlStateManager.popMatrix();
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        // Render the children
        if (areChildrenActive()) {
            for (BaseComponent component : children) {
                RenderUtils.prepareRenderState();
                component.renderOverlay(0, 0, mouseX, mouseY);
                RenderUtils.restoreColor();
                RenderUtils.restoreRenderState();
            }
        }
        GlStateManager.popMatrix();
    }

    /**
     * Used to find how wide this is
     *
     * @return How wide the component is
     */
    @Override
    public final int getWidth() {
        return currentWidth;
    }

    /**
     * Used to find how tall this is
     *
     * @return How tall the component is
     */
    @Override
    public final int getHeight() {
        return currentHeight;
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public final int getU() {
        return u;
    }

    public final void setU(int u) {
        this.u = u;
    }

    public final int getV() {
        return v;
    }

    public final void setV(int v) {
        this.v = v;
    }

    public final ItemStack getStack() {
        return stack;
    }

    public final void setStack(ItemStack stack) {
        this.stack = stack;
    }

    public final List<BaseComponent> getChildren() {
        return children;
    }

    public final void setChildren(List<BaseComponent> children) {
        this.children = children;
    }

    public final boolean isActive() {
        return isActive;
    }

    public final void setActive(boolean active) {
        isActive = active;
    }
}
