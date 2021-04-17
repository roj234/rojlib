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

package ilib.gui.comp.control;

import ilib.client.GuiHelper;
import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.util.TextHelperM;
import net.minecraft.client.gui.FontRenderer;

import javax.annotation.Nullable;
import java.awt.*;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class ComButton extends BaseComponent {
    //Variables
    protected int u, v, width, height;
    protected String label;

    /**
     * Constructor for the button component
     * <p>
     * Not texture
     */
    public ComButton(IGui parent, int xPos, int yPos, int w, int h, @Nullable String text) {
        super(parent, xPos, yPos);
        u = -1;
        v = -1;
        width = w;
        height = h;
        if (text != null)
            label = TextHelperM.translate(text);
        else
            label = null;
    }

    /**
     * Constructor for the button component
     * <p>
     * In your texture, you should put the hovered over texture directly below the main texture passed
     */
    public ComButton(IGui parent, int xPos, int yPos, int uPos, int vPos, int w, int h, @Nullable String text) {
        this(parent, xPos, yPos, uPos, vPos, w, h);
        if (text != null)
            label = TextHelperM.translate(text);
        else
            label = null;
    }

    public ComButton(IGui parent, int xPos, int yPos, int uPos, int vPos, int w, int h) {
        super(parent, xPos, yPos);
        u = uPos;
        v = vPos;
        width = w;
        height = h;
    }

    /**
     * Called when button is pressed
     */
    protected abstract void doAction();

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        GuiHelper.playButtonSound();
        doAction();
        super.mouseDown(mouseX, mouseY, button);
    }

    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (v >= 0) {
            RenderUtils.bindTexture(getTexture());
            drawTexturedModalRect(xPos, yPos, u, v, width, height);
        }
    }

    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (label != null) {
            final FontRenderer fr = owner.getFontRenderer();

            int size = fr.getStringWidth(label);

            fr.drawString(label, xPos + (width / 2f - size / 2f), yPos + 6, Color.darkGray.getRGB(), false);

        }
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = TextHelperM.translate(label);
    }

    public static class DisplayOnly extends ComButton {
        /**
         * Constructor for the display-only button component
         * <p>
         * In your texture, you should put the hovered over texture directly below the main texture passed
         */
        public DisplayOnly(IGui parent, int xPos, int yPos, int uPos, int vPos, int w, int h, @Nullable String text) {
            super(parent, xPos, yPos, uPos, vPos, w, h, text);
        }

        @Override
        protected void doAction() {
        }
    }
}
