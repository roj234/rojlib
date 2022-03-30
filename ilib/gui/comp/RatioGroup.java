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

import ilib.gui.util.ComponentListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class RatioGroup implements ComponentListener {
    protected List<GButton> checkboxes = new ArrayList<>();

    public RatioGroup() {}

    public GButton add(GButton checkbox) {
        checkboxes.add(checkbox);
        checkbox.setListener(this);
        return checkbox;
    }

    @Override
    public void mouseDown(Component c, int mouseX, int mouseY, int button) {
        int i = checkboxes.indexOf(c);
        if (i >= 0) {
            for (int j = 0; j < checkboxes.size(); j++) {
                checkboxes.get(j).setToggled(j == i);
            }
        }
    }

    public void selectFirst() {
        if (!checkboxes.isEmpty()) {
            GButton box = checkboxes.get(0);
            box.setToggled(true);
            box.doAction();
        }
    }
}
