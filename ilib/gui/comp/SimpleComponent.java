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

import ilib.gui.IGui;
import ilib.gui.util.SizeModifiable;

/**
 * @since 2021/1/13 12:37
 */
public abstract class SimpleComponent extends Component implements SizeModifiable {
    protected int width, height;

    public SimpleComponent(IGui parent) {
        super(parent);
    }

    public SimpleComponent(IGui parent, int x, int y) {
        super(parent, x, y);
    }

    public SimpleComponent(IGui parent, int x, int y, int w, int h) {
        super(parent, x, y);
        this.width = w;
        this.height = h;
    }

    @Override
    public void onInit() {
        if (xPos < 0) xPos += owner.getWidth();
        if (yPos < 0) yPos += owner.getHeight();
        if (width < 0) width = owner.getWidth() + width - xPos;
        if (height < 0) height = owner.getHeight() + height - yPos;

        super.onInit();
    }

    protected final boolean shouldInit() {
        return ((xPos | yPos | width | height) & 0x80000000) != 0;
    }

    @Override
    public void render(int mouseX, int mouseY) {}

    @Override
    public void render2(int mouseX, int mouseY) {}

    @Override
    public int getWidth() {
        return width;
    }

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
}
