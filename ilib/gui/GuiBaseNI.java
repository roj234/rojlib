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

import ilib.client.util.RenderUtils;
import ilib.gui.comp.BaseComponent;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class GuiBaseNI extends GuiScreen implements IGui {
    // Variables
    private final int xSize;
    private final int ySize;

    protected int imgX = 256;
    protected int imgY = 256;

    protected int guiLeft, guiTop;

    public ResourceLocation textureLocation;

    protected List<BaseComponent> components = new ArrayList<>();

    protected void setImageSize(int w, int h) {
        this.imgX = w;
        this.imgY = h;
    }

    @Nonnull
    @Override
    public FontRenderer getFontRenderer() {
        return fontRenderer;
    }

    public GuiBaseNI(int width, int height, ResourceLocation texture) {
        this.xSize = width;
        this.ySize = height;
        this.textureLocation = texture;
    }

    @Override
    public void initGui() {
        this.guiTop = (this.height - xSize) / 2;
        this.guiLeft = (this.width - ySize) / 2;
        components.clear();
        addComponents();
        for (BaseComponent component : components) {
            component.onInit();
        }
    }

    protected abstract void addComponents();

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

    public TileEntity getTileEntity() {
        return null;
    }

    @Override
    public ResourceLocation getTexture() {
        return this.textureLocation;
    }

    @Override
    protected void mouseClicked(int mouseX1, int mouseY1, int mouseButton) throws IOException {
        super.mouseClicked(mouseX1, mouseY1, mouseButton);
        final int mouseX = mouseX1 - guiLeft;
        final int mouseY = mouseY1 - guiTop;
        //if (mouseX < 0 || mouseY < 0) return;
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX, mouseY)) {
                component.mouseDown(mouseX, mouseY, mouseButton);
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX1, int mouseY1, int state) {
        super.mouseReleased(mouseX1, mouseY1, state);
        final int mouseX = mouseX1 - guiLeft;
        final int mouseY = mouseY1 - guiTop;
        //if (mouseX < 0 || mouseY < 0) return;
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX, mouseY)) {
                component.mouseUp(mouseX, mouseY, state);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX1, int mouseY1, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX1, mouseY1, clickedMouseButton, timeSinceLastClick);
        final int mouseX = mouseX1 - guiLeft;
        final int mouseY = mouseY1 - guiTop;
        //if (mouseX < 0 || mouseY < 0) return;
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX, mouseY)) {
                component.mouseDrag(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scrollDirection = Mouse.getEventDWheel();
        if (scrollDirection != 0) {
            int x = Mouse.getX(), y = Mouse.getY();
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

    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        RenderUtils.bindTexture(textureLocation);
        for (BaseComponent component : components) {
            RenderUtils.prepareRenderState();
            component.renderOverlay(0, 0, mouseX - guiLeft, mouseY - guiTop);
            RenderUtils.restoreRenderState();
        }
    }

    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        RenderUtils.prepareRenderState();
        GlStateManager.translate(guiLeft, guiTop, 0);

        RenderUtils.bindTexture(textureLocation);
        drawTexturedModalRect(0, 0, 0, 0, xSize + 1, ySize + 1);

        for (BaseComponent component : components) {
            RenderUtils.prepareRenderState();
            component.render(0, 0, mouseX - guiLeft, mouseY - guiTop);
            RenderUtils.restoreRenderState();
            RenderUtils.restoreColor();
        }
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.pushMatrix();

        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        this.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX - guiLeft, mouseY - guiTop))
                component.renderToolTip(mouseX, mouseY);
        }

        GlStateManager.translate((float) guiLeft, (float) guiTop, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawGuiContainerForegroundLayer(mouseX, mouseY);

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();

        GlStateManager.popMatrix();
    }

    @Override
    public int getGuiLeft() {
        return guiLeft;
    }

    @Override
    public int getGuiTop() {
        return guiTop;
    }

    @Override
    public int getXSize() {
        return xSize;
    }

    @Override
    public int getYSize() {
        return ySize;
    }

    public int getImgXSize() {
        return imgX;
    }

    public int getImgYSize() {
        return imgY;
    }
}
