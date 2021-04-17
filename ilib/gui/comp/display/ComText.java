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

import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.util.TextHelperM;
import net.minecraft.client.renderer.GlStateManager;
import roj.util.Color;

import javax.annotation.Nullable;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ComText extends BaseComponent {
    protected static final int COLOR_DEFAULT = 4210752;
    // Variables
    protected String text;
    protected Color color;

    /**
     * Creates the text component
     *
     * @param parent The gui parent
     * @param x      The x pos
     * @param y      The y pos
     * @param text   The string to render
     * @param color  Optional color
     */
    public ComText(IGui parent, int x, int y, String text, @Nullable Color color) {
        super(parent, x, y);
        this.text = TextHelperM.translate(text);
        this.color = color;
    }

    /*******************************************************************************************************************
     * BaseComponent                                                                                                   *
     *******************************************************************************************************************/

    /**
     * Called to render the component
     */
    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        // No Op
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();

        //GlStateManager.translate(xPos, yPos, 0);
        //RenderUtils.prepareRenderState();

        owner.getFontRenderer().drawString(text, xPos, yPos, color == null ? COLOR_DEFAULT : color.getARGB());

        //RenderUtils.restoreColor();
        //RenderUtils.restoreRenderState();

        GlStateManager.popMatrix();
    }

    /**
     * Used to find how wide this is
     *
     * @return How wide the component is
     */
    @Override
    public int getWidth() {
        return owner.getFontRenderer().getStringWidth(text);
    }

    /**
     * Used to find how tall this is
     *
     * @return How tall the component is
     */
    @Override
    public int getHeight() {
        return 7;
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
