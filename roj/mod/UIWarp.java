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
package roj.mod;

import roj.ui.UIUtil;

import java.io.IOException;

/**
 * UI Operation Wrap between Cmd and Window
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/19 19:59
 */
class UIWarp {
    boolean getBoolean(String msg) throws IOException {
        return UIUtil.readBoolean(msg);
    }

    int getNumberInRange(int min, int max) throws IOException {
        return UIUtil.getNumberInRange(min, max);
    }

    String userInput(String msg, boolean optional) throws IOException {
        return UIUtil.userInput(msg);
    }

    void stageInfo(int id, boolean... flags) {}

    boolean isConsole() {
        return true;
    }
}
