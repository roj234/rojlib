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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: GuiBase.java
 */
package ilib.gui;

import ilib.client.RenderUtils;
import ilib.gui.comp.Component;
import ilib.gui.comp.GTabs;
import ilib.gui.comp.GText;
import ilib.gui.util.GuiListener;
import org.lwjgl.input.Mouse;
import roj.collect.SimpleList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public abstract class GuiBase<T extends ContainerIL> extends GuiContainer implements IGui {
    protected String name;

    protected T inventory;

    protected int texX = 256, texY = 256;
    public ResourceLocation tex;

    protected GTabs rightTabs, leftTabs;

    protected GuiListener listener;

    protected List<Component> components = new SimpleList<>();
    private List<Component> clicked = new SimpleList<>();

    protected final void setImageSize(int w, int h) {
        this.texX = w;
        this.texY = h;
    }

    public GuiBase(T inventory, int width, int height, String title, ResourceLocation texture) {
        super(inventory);
        this.xSize = width;
        this.ySize = height;
        this.name = title;
        this.tex = texture;
        this.inventory = inventory;
    }

    protected void init() {
        initComponents();
        initTabs();
    }

    @Override
    public void initGui() {
        super.initGui();

        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            com.onInit();
        }

        init();
    }

    protected void initComponents() {
        rightTabs = new GTabs(this, xSize + 1);
        leftTabs = new GTabs(this, -1);

        components.add(GText.alignCenterY(this, 3, name, null));

        addComponents();
    }

    protected void initTabs() {
        addRightTabs(rightTabs);
        addLeftTabs(leftTabs);

        components.add(rightTabs);
        components.add(leftTabs);
    }

    protected abstract void addComponents();

    protected void addRightTabs(GTabs tabs) {
    }

    protected void addLeftTabs(GTabs tabs) {
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        mouseX -= guiLeft;
        mouseY -= guiTop;

        if (mc.player.inventory.getItemStack().isEmpty() && hoveredSlot != null && hoveredSlot.getHasStack()) {
            this.renderToolTip(hoveredSlot.getStack(), mouseX, mouseY);
        }

        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            if (com.isMouseOver(mouseX, mouseY)) {
                com.renderToolTip(mouseX, mouseY);
            }
        }
    }

    public final void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
        if (texX == 256 && texY == 256) {
            if (w > 256 || h > 256)
                drawScaledCustomSizeModalRect(x, y, u, v, w, h, 256, 256, 256, 256);
            else
                RenderUtils.fastRect(x, y, u, v, w, h);
        } else {
            RenderUtils.fastRect(x, y, u, v, w, h, texX, texY);
        }
    }

    @Override
    public TileEntity getTileEntity() { // slow
        return null;
    }

    @Override
    public final ResourceLocation getTexture() {
        return this.tex;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button);

        x -= guiLeft;
        y -= guiTop;

        if (listener != null) listener.mouseDown(x, y, button);

        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            if (com.isMouseOver(x, y)) {
                com.mouseDown(x, y, button);
                clicked.add(com);
            }
        }
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        super.mouseReleased(x, y, button);

        x -= guiLeft;
        y -= guiTop;

        if (listener != null) listener.mouseUp(x, y, button);

        for (int i = 0; i < clicked.size(); i++) {
            clicked.get(i).mouseUp(x, y, button);
        }
        clicked.clear();
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        x -= guiLeft;
        y -= guiTop;

        if (listener != null) listener.mouseDrag(x, y, button, time);

        for (int i = 0; i < clicked.size(); i++) {
            clicked.get(i).mouseDrag(x, y, button, time);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int dir = Mouse.getEventDWheel();
        if (dir != 0) {
            int x = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
            int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop;

            if (listener != null) listener.mouseScrolled(x, y, dir);

            for (int i = 0; i < components.size(); i++) {
                Component com = components.get(i);
                com.mouseScrolled(x, y, dir / 120);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            com.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GuiHelper.renderForeground(mouseX - guiLeft, mouseY - guiTop, components);

        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawBackgroundImage();

        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0);

        GuiHelper.renderBackground(mouseX - guiLeft, mouseY - guiTop, components);

        GlStateManager.popMatrix();
    }

    protected void drawBackgroundImage() {
        drawWorldBackground(0);

        RenderUtils.bindTexture(tex);
        RenderUtils.fastRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    public final int getLeft() {
        return guiLeft;
    }

    public final int getTop() {
        return guiTop;
    }

    public final int getWidth() {
        return xSize;
    }

    public final int getHeight() {
        return ySize;
    }

    public final List<Rectangle> getCoveredAreas(List<Rectangle> areas) {
        areas.add(new Rectangle(guiLeft, guiTop, xSize, ySize));
        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            if (com instanceof GTabs) {
                GTabs tabCollection = (GTabs) com;
                areas.addAll(tabCollection.getAreasCovered(guiLeft, guiTop));
            } else {
                areas.add(new Rectangle(com.getArea(guiLeft, guiTop)));
            }
        }
        return areas;
    }

    public GuiListener getListener() {
        return listener;
    }

    public void setListener(GuiListener listener) {
        this.listener = listener;
    }
}
