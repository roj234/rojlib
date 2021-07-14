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

import ilib.client.util.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import roj.util.Color;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/13 12:37
 */
public class NinePatchRenderer {
    // Variables
    protected int u, v, cellSize;
    protected ResourceLocation patchLocation;

    /**
     * Creates a renderer with given options
     * <p>
     * Texture must be in the following pattern. Cell size is how many pixels each box is (relative to x256)
     * <p>
     * *---*---*---*
     * |   |   |   |
     * |   |   |   |
     * *---*---*---*
     * |   |   |   |
     * |   |   |   |
     * *---*---*---*
     * |   |   |   |
     * |   |   |   |
     * *---*---*---*
     * <p>
     * Corners will render one to one
     * Edges will be stretched on their axis
     * Middle will be expanded in both axis (must be solid color)
     *
     * @param U       The texture U location
     * @param V       The texture V location
     * @param size    The cell size
     * @param texture The texture location
     */
    public NinePatchRenderer(int U, int V, int size, ResourceLocation texture) {
        u = U;
        v = V;
        cellSize = size;
        patchLocation = texture;
    }

    public void setTexture(ResourceLocation loc) {
        this.patchLocation = loc;
    }

    /**
     * Partial Rendering Code
     * <p>
     * This can be overwritten in a new INSTANCE of the class to disable certain parts from rendering or to give them a
     * different behavior. One INSTANCE would be for a tab, you can prevent the left edge from rendering in that way
     */

    // Corners
    protected void renderTopLeftCorner(Gui gui) {
        gui.drawTexturedModalRect(0, 0, u, v, cellSize, cellSize);
    }

    protected void renderTopRightCorner(Gui gui, int width) {
        gui.drawTexturedModalRect(width - cellSize, 0, u + cellSize + cellSize, v, cellSize, cellSize);
    }

    protected void renderBottomLeftCorner(Gui gui, int height) {
        gui.drawTexturedModalRect(0, height - cellSize, u, v + cellSize + cellSize, cellSize, cellSize);
    }

    protected void renderBottomRightCorner(Gui gui, int width, int height) {
        gui.drawTexturedModalRect(width - cellSize, height - cellSize, u + cellSize + cellSize, v + cellSize + cellSize, cellSize, cellSize);
    }

    // Edges
    protected void renderTopEdge(Gui gui, int width) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cellSize, 0, 0);
        GL11.glScalef(width - (cellSize * 2), 1, 0);
        gui.drawTexturedModalRect(0, 0, u + cellSize, v, 1, cellSize);
        GL11.glPopMatrix();
    }

    protected void renderBottomEdge(Gui gui, int width, int height) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cellSize, height - cellSize, 0);
        GL11.glScalef(width - (cellSize * 2), 1, 0);
        gui.drawTexturedModalRect(0, 0, u + cellSize, v + cellSize + cellSize, 1, cellSize);
        GL11.glPopMatrix();
    }

    protected void renderLeftEdge(Gui gui, int height) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0, cellSize, 0);
        GL11.glScalef(1, height - (cellSize * 2), 0);
        gui.drawTexturedModalRect(0, 0, u, v + cellSize, cellSize, 1);
        GL11.glPopMatrix();
    }

    protected void renderRightEdge(Gui gui, int width, int height) {
        GL11.glPushMatrix();
        GL11.glTranslatef(width - cellSize, cellSize, 0);
        GL11.glScalef(1, height - (cellSize * 2), 0);
        gui.drawTexturedModalRect(0, 0, u + cellSize + cellSize, v + cellSize, cellSize, 1);
        GL11.glPopMatrix();
    }

    // Background
    protected void renderBackground(Gui gui, int width, int height) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cellSize - 1, cellSize - 1, 0);
        GL11.glScalef(width - (cellSize * 2) + 2, height - (cellSize * 2) + 2, 0);
        gui.drawTexturedModalRect(0, 0, u + cellSize, v + cellSize, 1, 1);
        GL11.glPopMatrix();
    }

    public void render(Gui gui, int x, int y, int width, int height) {
        render(gui, x, y, width, height, null);
    }

    /**
     * mi.Main render call. This must be called in the parent gui to render the box.
     * <p>
     * WARNING: Will bind texture to sheet, make sure you rebind afterwards or do this first
     *
     * @param gui    The screen being rendered onto
     * @param x      Screen X Position
     * @param y      Screen Y Position
     * @param width  Width
     * @param height Height
     * @param color  Color to render
     */
    public void render(Gui gui, int x, int y, int width, int height, Color color) {
        GL11.glPushMatrix();
        if (color != null)
            RenderUtils.setColor(color);
        if (patchLocation != null)
            RenderUtils.bindTexture(patchLocation);
        GL11.glTranslatef(x, y, 0);
        renderBackground(gui, width, height);
        renderTopEdge(gui, width);
        renderBottomEdge(gui, width, height);
        renderRightEdge(gui, width, height);
        renderLeftEdge(gui, height);
        renderTopLeftCorner(gui);
        renderTopRightCorner(gui, width);
        renderBottomLeftCorner(gui, height);
        renderBottomRightCorner(gui, width, height);
        GL11.glPopMatrix();
    }
}
