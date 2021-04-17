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

import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class ComGroup extends BaseComponent {
    protected int u, v;
    protected boolean isActive = false;

    protected List<BaseComponent> children;

    public ComGroup(IGui parent, int x, int y, int u, int v) {
        super(parent, x, y);
        this.u = u;
        this.v = v;

        children = new ArrayList<>();
    }

    public final ComGroup addChild(BaseComponent component) {
        children.add(component);
        return this;
    }

    /**
     * Moves the slots if need be
     */
    public final void moveSlots() {
        for (Object component : children) {
            if (component instanceof ComSlot)
                ((ComSlot) component).display(isActive());
        }
    }

    public final boolean mouseDownActivated(int x, int y, int button) {
        if (this.guiListener != null)
            this.guiListener.onMouseDown(this, x, y, button);
        if (isActive()) {
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
        if (isActive()) {
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
        if (isActive()) {
            for (BaseComponent component : children) {
                if (component.isMouseOver(x, y)) {
                    component.mouseDrag(x, y, button, time);
                    return true;
                }
            }
        }
        return false;
    }

    public final void mouseScrolled(int x, int y, int dir) {
        if(!isMouseOver(x, y))
            return;
        if (isActive()) {
            for (BaseComponent component : children) {
                component.mouseScrolled(x, y, dir);
            }
        }
    }

    /*******************************************************************************************************************
     * BaseComponent                                                                                                   *
     *******************************************************************************************************************/

    @Override
    public final void keyTyped(char letter, int keyCode) {
        for (BaseComponent component : children) {
            component.keyTyped(letter, keyCode);
        }
    }

    @Override
    public final void renderToolTip(int mouseX, int mouseY) {
        if (isActive()) {
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
    public final void render(int guiLeft, int guiTop, int mouseX, int mouseY) {

        // Render the children
        if (isActive()) {
            GlStateManager.pushMatrix();
            for (BaseComponent component : children) {
                RenderUtils.prepareRenderState();
                component.render(guiLeft, guiTop, mouseX, mouseY);
                RenderUtils.restoreColor();
                RenderUtils.restoreRenderState();
            }
            GlStateManager.popMatrix();
        }

    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public final void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        // Render the children
        if (isActive()) {
            for (BaseComponent component : children) {
                RenderUtils.prepareRenderState();
                component.renderOverlay(guiLeft, guiTop, mouseX, mouseY);
                RenderUtils.restoreColor();
                RenderUtils.restoreRenderState();
            }
        }
    }

    @Override
    public final int getWidth() {
        return u;
    }

    @Override
    public final int getHeight() {
        return v;
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

    public final boolean isActive() {
        return isActive;
    }

    public final void setActive(boolean active) {
        isActive = active;
    }
}
