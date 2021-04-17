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

import ilib.client.util.RenderUtils;
import ilib.gui.IGui;

import javax.annotation.Nullable;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class ComCheckbox extends ComButtonOver {
    public ComCheckbox(IGui parent, int xPos, int yPos, int uPos, int vPos, int w, int h) {
        super(parent, xPos, yPos, uPos, vPos, w, h, null);
    }

    /**
     * Constructor for the button component
     * <p>
     * Not texture
     */
    public ComCheckbox(IGui parent, int xPos, int yPos, int w, int h, @Nullable String text) {
        super(parent, xPos, yPos, w, h, text);
    }

    /**
     * Constructor for the button component
     * <p>
     * In your texture, you should put the hovered over texture directly below the main texture passed
     */
    public ComCheckbox(IGui parent, int xPos, int yPos, int uPos, int vPos, int w, int h, @Nullable String text) {
        super(parent, xPos, yPos, uPos, vPos, w, h, text);
    }

    protected abstract void doAction(boolean status);

    @Override
    protected final void doAction() {
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= xPos && mouseX < xPos + width && mouseY >= yPos && mouseY < yPos + height;
    }


    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (v >= 0) {
            RenderUtils.bindTexture(getTexture());
            drawTexturedModalRect(xPos, yPos, u, isOver ? v + height : v, width, height);
        }
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        isOver = !isOver;
        doAction(isOver);
        super.mouseDown(mouseX, mouseY, button);
    }
}
