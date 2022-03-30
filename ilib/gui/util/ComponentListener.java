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
 * Filename: <Filename>.java
 */
package ilib.gui.util;

import ilib.gui.comp.Component;

import java.util.List;

public interface ComponentListener {
    int BUTTON_CLICKED = 0, TEXT_CHANGED = 1, SLIDER_MOVED = 2;

    default void componentAdded(Component c) {}

    default void actionPerformed(Component c, int action) {}

    default void keyTyped(Component c, char character, int keyCode) {}

    default void mouseDown(Component c, int mouseX, int mouseY, int button) {}

    default void mouseUp(Component c, int mouseX, int mouseY, int button) {}

    default void mouseDrag(Component c, int mouseX, int mouseY, int button, long time) {}

    default void mouseScrolled(Component c, int mouseX, int mouseY, int dir) {}

    default void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {}
}
