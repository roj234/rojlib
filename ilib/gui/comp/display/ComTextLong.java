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

import ilib.client.GuiHelper;
import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.util.Colors;
import ilib.util.TextHelperM;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import roj.math.MathUtils;
import roj.util.Color;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ComTextLong extends BaseComponent {
    // Variables
    protected int width, height, u, v, textScale;
    protected boolean upSelected, downSelected = false;
    protected Color color = Colors.WHITE.getColor();
    protected int lineWidth, currentLine;
    protected List<String> lines;

    /**
     * Creates the long text object
     * <p>
     * IMPORTANT: The up and down arrows should be together, up on top down on bottom. Provide u and v for top left of top
     * Arrow size should be 15x8 pixels
     *
     * @param parent    The parent GUI
     * @param x         The x pos
     * @param y         The y pos
     * @param w         The width
     * @param h         The height
     * @param u         The arrows u
     * @param v         The arrows v
     * @param text      The text to display
     * @param textScale The text scale, default size is 100
     */
    public ComTextLong(IGui parent, int x, int y, int u, int v, int w, int h, String text, int textScale, Color color) {
        this(parent, x, y, w, h, u, v, text, textScale);
        this.color = color;
    }

    public ComTextLong(IGui parent, int x, int y, int u, int v, int w, int h, String text, int textScale) {
        super(parent, x, y);
        if (text == null) return;
        this.width = w;
        this.height = h;
        this.u = u;
        this.v = v;
        this.textScale = textScale;

        currentLine = 0;
        lineWidth = (int) ((100f / (float) textScale) * (float) Math.max(18, width - 14));

        // Setup Lines
        lines = TextHelperM.splitString(parent.getFontRenderer(), TextHelperM.translate(text), lineWidth);
    }

    /**
     * Used to get the last line to render on screen
     *
     * @return The last index to render
     */
    private int getLastLineToRender() {
        int maxOnScreen = (int) (((float) height) / ((((float) textScale) * 9f) / 100f));
        return Math.max(lines.size() - maxOnScreen, 0);
    }

    /**
     * Called when the mouse is pressed
     *
     * @param x      Mouse X Position
     * @param y      Mouse Y Position
     * @param button Mouse Button
     */
    @Override
    public void mouseDown(int x, int y, int button) {
        if (GuiHelper.isInBounds(x, y, xPos + width - 15, yPos, xPos + width, yPos + 8)) {
            upSelected = true;
            currentLine -= 1;
            if (currentLine < 0)
                currentLine = 0;
            GuiHelper.playButtonSound();
        } else if (GuiHelper.isInBounds(x, y, xPos + width - 15, yPos + height - 8, x + width, yPos + height)) {
            downSelected = true;
            currentLine += 1;
            if (currentLine > getLastLineToRender())
                currentLine = getLastLineToRender();
            GuiHelper.playButtonSound();
        }
    }

    /**
     * Called when the mouse button is over the component and released
     *
     * @param x      Mouse X Position
     * @param y      Mouse Y Position
     * @param button Mouse Button
     */
    @Override
    public void mouseUp(int x, int y, int button) {
        upSelected = downSelected = false;
    }

    /**
     * Called when the mouse is scrolled
     *
     * @param dir 1 for positive, -1 for negative
     */
    @Override
    public void mouseScrolled(int x, int y, int dir) {
        if(!isMouseOver(x, y))
            return;
        currentLine = MathUtils.clamp(currentLine - dir, 0, getLastLineToRender());
    }

    /**
     * Called to render the component
     */
    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(xPos, yPos, 0);
        RenderUtils.bindTexture(getTexture());

        drawTexturedModalRect(width - 15, 0, u, v, 15, 8);
        drawTexturedModalRect(width - 15, height - 7, u, v + 8, 15, 8);
        GlStateManager.popMatrix();
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();

        GlStateManager.translate(xPos, yPos, 0);
        RenderUtils.prepareRenderState();

        FontRenderer fr = owner.getFontRenderer();

        //boolean uniCode = fr.getUnicodeFlag();
        //fr.setUnicodeFlag(false);

        int yPos = 0;
        int actualY = 0;

        int eachHeight = (textScale * fr.FONT_HEIGHT) / 100;

        GlStateManager.scale(textScale / 100F, textScale / 100F, textScale / 100F);
        for (int x = currentLine; x < lines.size(); x++) {
            if (actualY + eachHeight > height)
                break;
            RenderUtils.restoreColor();

            fr.drawString(lines.get(x), 0, yPos, color.getRGB());
            yPos += fr.FONT_HEIGHT;
            actualY += eachHeight;
        }

        //fr.setUnicodeFlag(uniCode);
        GlStateManager.popMatrix();
    }

    /**
     * Used to find how wide this is
     *
     * @return How wide the component is
     */
    @Override
    public int getWidth() {
        return width;
    }

    /**
     * Used to find how tall this is
     *
     * @return How tall the component is
     */
    @Override
    public int getHeight() {
        return height;
    }


    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getU() {
        return u;
    }

    public void setU(int u) {
        this.u = u;
    }

    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }

    public int getTextScale() {
        return textScale;
    }

    public void setTextScale(int textScale) {
        this.textScale = textScale;
    }

    public int getCurrentLine() {
        return currentLine;
    }

    public void setCurrentLine(int currentLine) {
        this.currentLine = currentLine;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }
}
