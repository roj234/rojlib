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

import ilib.anim.Animation;
import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GGroup extends SimpleComponent implements IGui {
    protected boolean active;

    protected SimpleList<Component> components;
    private SimpleList<Component> clicked = new SimpleList<>();

    public GGroup(IGui parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.active = true;

        components = new SimpleList<>();
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    public final GGroup append(Component com) {
        components.add(com);
        return this;
    }

    public SimpleList<Component> getComponents() {
        return components;
    }

    @Override
    public void onInit() {
        super.onInit();
        for (int i = 0; i < components.size(); i++) {
            components.get(i).onInit();
        }
    }

    /**
     * toggle display
     */
    public final void moveSlots() {
        for (int i = 0; i < components.size(); i++) {
            Object component = components.get(i);
            if (component instanceof GSlot) {
                ((GSlot) component).setVisible(isActive());
            }
        }
    }

    @Override
    public void mouseDown(int x, int y, int button) {
        if (isActive()) {
            super.mouseDown(x, y, button);

            x -= xPos;
            y -= yPos;
            for (int i = 0; i < components.size(); i++) {
                Component com = components.get(i);
                if (com.isMouseOver(x, y)) {
                    com.mouseDown(x, y, button);
                    clicked.add(com);
                }
            }
        }
    }

    @Override
    public void mouseUp(int x, int y, int button) {
        if (isActive()) {
            super.mouseUp(x, y, button);

            x -= xPos;
            y -= yPos;
            for (int i = 0; i < clicked.size(); i++) {
                clicked.get(i).mouseUp(x, y, button);
            }
            clicked.clear();
        }
    }

    @Override
    public void mouseDrag(int x, int y, int button, long time) {
        if (isActive()) {
            super.mouseDrag(x, y, button, time);

            x -= xPos;
            y -= yPos;
            for (int i = 0; i < clicked.size(); i++) {
                clicked.get(i).mouseDrag(x, y, button, time);
            }
        }
    }

    @Override
    public void mouseScrolled(int x, int y, int dir) {
        if (isActive()) {
            if(!isMouseOver(x, y)) return;

            x -= xPos;
            y -= yPos;
            for (int i = 0; i < components.size(); i++) {
                Component com = components.get(i);
                com.mouseScrolled(x, y, dir);
            }
        }
    }

    @Override
    public void keyTyped(char letter, int keyCode) {
        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            com.keyTyped(letter, keyCode);
        }
    }

    @Override
    public void renderToolTip(int x, int y) {
        if (isActive()) {
            x -= xPos;
            y -= yPos;

            GlStateManager.pushMatrix();
            GL11.glTranslatef(xPos, yPos, 0);
            for (int i = 0; i < components.size(); i++) {
                Component com = components.get(i);
                if (com.isMouseOver(x, y)) {
                    com.renderToolTip(x, y);
                }
            }
            GlStateManager.popMatrix();
        } else super.renderToolTip(x, y);
    }

    @Override
    public final void render(int mouseX, int mouseY) {
        // Render the children
        if (isActive()) {
            mouseX -= xPos;
            mouseY -= yPos;

            GlStateManager.pushMatrix();
            GL11.glTranslatef(xPos, yPos, 0);
            for (int i = 0; i < components.size(); i++) {
                RenderUtils.prepareRenderState();

                Component com = components.get(i);
                Animation anim = com.getAnimation();
                if (anim != null) {
                    GlStateManager.pushMatrix();
                    anim.apply();
                }
                com.render(mouseX, mouseY);
                if (anim != null) {
                    GlStateManager.popMatrix();
                    if (!anim.isPlaying()) com.setAnimation(null);
                }
            }
            RenderUtils.restoreRenderState();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public final void renderOverlay(int mouseX, int mouseY) {
        // Render the children
        if (isActive()) {
            mouseX -= xPos;
            mouseY -= yPos;

            GlStateManager.pushMatrix();
            GL11.glTranslatef(xPos, yPos, 0);
            for (int i = 0; i < components.size(); i++) {
                RenderUtils.prepareRenderState();

                Component com = components.get(i);
                Animation anim = com.getAnimation();
                if (anim != null) {
                    GlStateManager.pushMatrix();
                    anim.apply();
                }
                com.renderOverlay(mouseX, mouseY);
                if (anim != null) {
                    GlStateManager.popMatrix();
                    if (!anim.isPlaying()) com.setAnimation(null);
                }
            }
            RenderUtils.restoreRenderState();
            GlStateManager.popMatrix();
        }
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public final boolean isActive() {
        return active;
    }

    public final void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public GGroup setListener(ComponentListener l) {
        this.listener = l;
        if (l != null) l.componentAdded(this);
        if (components != null) {
            for (int i = 0; i < components.size(); i++) {
                components.get(i).setListener(l);
            }
        }
        return this;
    }

    @Override
    public TileEntity getTileEntity() {
        return null;
    }

    @Override
    public int getTop() {
        return yPos + owner.getTop();
    }

    @Override
    public int getLeft() {
        return xPos + owner.getLeft();
    }
}
