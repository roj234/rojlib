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

import ilib.ClientProxy;
import ilib.client.util.RenderUtils;
import ilib.gui.comp.BaseComponent;
import ilib.gui.comp.display.ComTabs;
import ilib.gui.comp.display.ComText;
import ilib.util.PlayerUtil;
import ilib.util.TextHelper;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nonnull;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class GuiBase<T extends ContainerEmpty> extends GuiContainer implements IGui {
    // Variables
    protected String name;
    protected ComText titleComponent;

    protected T inventory;

    public ResourceLocation textureLocation;

    protected List<BaseComponent> components = new ArrayList<>();
    protected ComTabs rightTabs, leftTabs;

    protected int imgX = 256;
    protected int imgY = 256;

    protected final void setImageSize(int w, int h) {
        this.imgX = w;
        this.imgY = h;
    }

    @Nonnull
    @Override
    public final FontRenderer getFontRenderer() {
        if (fontRenderer != null)
            return fontRenderer;
        return ClientProxy.mc.fontRenderer;
    }

    public GuiBase(T inventory, int width, int height, String title, ResourceLocation texture) {
        super(inventory);
        this.xSize = width;
        this.ySize = height;
        this.name = title;
        this.textureLocation = texture;
        this.inventory = inventory;
    }

    protected void init() {
        initComponents();
        initTabs();
    }

    @Override
    public void initGui() {
        super.initGui();

        for (BaseComponent component : components) {
            component.onInit();
        }
    }

    protected void initComponents() {
        rightTabs = new ComTabs(this, xSize + 1);
        leftTabs = new ComTabs(this, -1);

        titleComponent = new ComText(this,
                xSize / 2 - (getFontRenderer().getStringWidth(TextHelper.translate(name)) / 2),
                3, name, null);
        components.add(titleComponent);

        addComponents();
    }

    protected void initTabs() {
        addRightTabs(rightTabs);
        addLeftTabs(leftTabs);

        components.add(rightTabs);
        components.add(leftTabs);
    }

    protected abstract void addComponents();

    protected void addRightTabs(ComTabs tabs) {
    }

    protected void addLeftTabs(ComTabs tabs) {
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            drawDefaultBackground();
        } catch (Exception ignored) {
        }

        GlStateManager.pushMatrix();
        this.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
        GlStateManager.popMatrix();

        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        //GlStateManager.disableLighting();
        //GlStateManager.disableDepth();

        //super.super.drawScreen(mouseX, mouseY, partialTicks);

        RenderHelper.enableGUIStandardItemLighting();

        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0);
        GlStateManager.enableRescaleNormal();
        this.hoveredSlot = null;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);

        RenderUtils.colorWhite();
        for (int i = 0, len = this.inventorySlots.inventorySlots.size(); i < len; i++) {
            Slot slot = this.inventorySlots.inventorySlots.get(i);
            if (shouldSlotBeRendered(slot)) {
                this.drawSlot(slot);
                if (this.isMouseOverSlot(slot, mouseX, mouseY)) {
                    this.hoveredSlot = slot;
                    GlStateManager.disableLighting();
                    GlStateManager.disableDepth();
                    GlStateManager.colorMask(true, true, true, false);
                    this.drawGradientRect(slot.xPos, slot.yPos, slot.xPos + 16, slot.yPos + 16, -2130706433, -2130706433);
                    GlStateManager.colorMask(true, true, true, true);
                    GlStateManager.enableDepth();
                    GlStateManager.enableLighting();
                }
            }

        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.pushMatrix();
        this.drawGuiContainerForegroundLayer(mouseX, mouseY);
        GlStateManager.popMatrix();
        RenderHelper.enableGUIStandardItemLighting();

        InventoryPlayer inventoryplayer = this.mc.player.inventory;
        ItemStack stack = this.draggedStack.isEmpty() ? inventoryplayer.getItemStack() : this.draggedStack;
        if (!stack.isEmpty()) {
            String s = null;
            if (!this.draggedStack.isEmpty() && this.isRightMouseClick) {
                stack = stack.copy();
                stack.setCount(MathHelper.ceil((float) stack.getCount() / 2.0F));
            } else if (this.dragSplitting && this.dragSplittingSlots.size() > 1) {
                stack = stack.copy();
                stack.setCount(this.dragSplittingRemnant);
            }

            int off = this.draggedStack.isEmpty() ? 8 : 16;
            this.drawItemStack(stack, mouseX - guiLeft - 8, mouseY - guiTop - off, stack.isEmpty() ? "\u00a7e0" : null);
        }

        if (!this.returningStack.isEmpty()) {
            float f = (float) (Minecraft.getSystemTime() - this.returningStackTime) / 100.0F;
            if (f >= 1.0F) {
                f = 1.0F;
                this.returningStack = ItemStack.EMPTY;
            }

            int l2 = this.returningStackDestSlot.xPos - this.touchUpX;
            int i3 = this.returningStackDestSlot.yPos - this.touchUpY;
            int l1 = this.touchUpX + (int) ((float) l2 * f);
            int i2 = this.touchUpY + (int) ((float) i3 * f);
            this.drawItemStack(this.returningStack, l1, i2, null);
        }

        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();

        renderHoveredToolTip(mouseX, mouseY);
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX - guiLeft, mouseY - guiTop)) {
                component.renderToolTip(mouseX, mouseY);
            }
        }
    }

    protected boolean shouldSlotBeRendered(Slot slot) {
        return slot.isEnabled();
    }

    public final void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
        if (imgX == 256 && imgY == 256) {
            if (w > 256 || h > 256)
                drawScaledCustomSizeModalRect(x, y, u, v, w, h, 256, 256, 256, 256);
            else
                RenderUtils.fastRect(x, y, u, v, w, h);
        } else {
            RenderUtils.fastRect(x, y, u, v, w, h, imgX, imgY);
        }
    }

    @Override
    public TileEntity getTileEntity() { // slow
        return null;
    }

    @Override
    public final ResourceLocation getTexture() {
        return this.textureLocation;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX - guiLeft, mouseY - guiTop)) {
                component.mouseDown(mouseX - guiLeft, mouseY - guiTop, mouseButton);
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);

        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX - guiLeft, mouseY - guiTop)) {
                component.mouseUp(mouseX - guiLeft, mouseY - guiTop, state);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX - guiLeft, mouseY - guiTop)) {
                component.mouseDrag(mouseX - guiLeft, mouseY - guiTop, clickedMouseButton, timeSinceLastClick);
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scrollDirection = Mouse.getEventDWheel();
        if (scrollDirection != 0) {
            int x = Mouse.getX(), y = Mouse.getY();
            PlayerUtil.broadcastAll("MX " + x +" MY " + y);
            for (BaseComponent component : components) {
                component.mouseScrolled(x, y, scrollDirection > 0 ? 1 : -1);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        for (BaseComponent component : components) {
            component.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        RenderUtils.bindTexture(textureLocation);
        for (BaseComponent component : components) {
            RenderUtils.prepareRenderState();
            component.renderOverlay(0, 0, mouseX - guiLeft, mouseY - guiTop);
            RenderUtils.restoreRenderState();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        RenderUtils.prepareRenderState();
        GlStateManager.translate(guiLeft, guiTop, 0);

        RenderUtils.bindTexture(textureLocation);
        drawTexturedModalRect(0, 0, 0, 0, xSize + 1, ySize + 1);

        for (BaseComponent component : components) {
            GlStateManager.pushMatrix();
            component.render(0, 0, mouseX - guiLeft, mouseY - guiTop);
            GlStateManager.popMatrix();
        }
        RenderUtils.restoreRenderState();
        RenderUtils.restoreColor();
    }

    public final int getGuiLeft() {
        return guiLeft;
    }

    public final int getGuiTop() {
        return guiTop;
    }

    public final int getXSize() {
        return xSize;
    }

    public final int getYSize() {
        return ySize;
    }

    public final int getImgXSize() {
        return imgX;
    }

    public final int getImgYSize() {
        return imgY;
    }

    public final List<Rectangle> getCoveredAreas(List<Rectangle> areas) {
        areas.add(new Rectangle(guiLeft, guiTop, xSize, ySize));
        for (BaseComponent component : components) {
            if (component instanceof ComTabs) {
                ComTabs tabCollection = (ComTabs) component;
                areas.addAll(tabCollection.getAreasCovered(guiLeft, guiTop));
            } else
                areas.add(new Rectangle(component.getArea(guiLeft, guiTop)));
        }
        return areas;
    }
}
