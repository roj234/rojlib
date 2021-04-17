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
import roj.util.Int2IntFunction;

import java.util.List;
import java.util.function.Consumer;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class ComTexAnimLambda extends ComTextureAnimated {
    private final Int2IntFunction function;
    private Consumer<List<String>> tooltip;

    public ComTexAnimLambda(IGui parent, int x, int y, int texU, int texV, int imageWidth, int imageHeight, Direction dir, Int2IntFunction function) {
        super(parent, x, y, texU, texV, imageWidth, imageHeight, dir);
        this.function = function;
    }

    public ComTexAnimLambda(IGui parent, int x, int y, int texU, int texV, int imageWidth, int imageHeight, Direction dir, Int2IntFunction function, Consumer<List<String>> tooltip) {
        super(parent, x, y, texU, texV, imageWidth, imageHeight, dir);
        this.function = function;
        this.tooltip = tooltip;
    }

    protected int getProgress(int scale) {
        return function.func(scale);
    }

    @Override
    public void getDynamicToolTip(List<String> toolTip, int mouseX, int mouseY) {
        if (tooltip != null)
            tooltip.accept(toolTip);
    }
}
