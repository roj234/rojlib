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
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GNumberInput extends GTextInput {
    protected int val, min, max;

    public GNumberInput(IGui parent, int x, int y, int width, int height, int value, int min, int max) {
        super(parent, x, y, width, height, Integer.toString(value));
        this.val = value;
        this.min = min;
        this.max = max;
        this.maxLength = 12;
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    @Deprecated
    protected void onChange(int value) {
    }

    @Override
    protected void onChange(String value) {
        val = Integer.parseInt(getText());
        onChange(val);

        super.onChange(value);
    }

    @Override
    protected boolean isValidText(String text) {
        if (TextUtil.isNumber(text) != 0) return false;
        int i = Integer.parseInt(text);
        if (i < min) setText(String.valueOf(min));
        else if (i > max) setText(String.valueOf(max));
        return true;
    }

    @Override
    protected boolean isValidChar(char letter, int keyCode) {
        return letter == '.' || letter == '-' || Character.isDigit(letter);
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public int value() {
        return val;
    }

    public int min() {
        return min;
    }

    public void min(int min) {
        this.min = min;
    }

    public int max() {
        return max;
    }

    public void max(int max) {
        this.max = max;
    }
}
