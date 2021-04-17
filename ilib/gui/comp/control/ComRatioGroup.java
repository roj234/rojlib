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

import ilib.gui.comp.BaseComponent;
import ilib.gui.comp.IGuiListener;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ComRatioGroup implements IGuiListener {
    protected List<ComCheckbox> checkboxes = new ArrayList<>();

    public ComRatioGroup() {

    }

    public ComCheckbox add(ComCheckbox checkbox) {
        checkboxes.add(checkbox);
        checkbox.setGuiListener(this);
        return checkbox;
    }

    /**
     * Called when the mouse clicks on the component
     *
     * @param component The component to be clicked
     * @param mouseX    X position of the mouse
     * @param mouseY    Y position of the mouse
     * @param button    Which button was clicked
     */
    @Override
    public void onMouseDown(BaseComponent component, int mouseX, int mouseY, int button) {
        if (component.isMouseOver(mouseX, mouseY)) {
            int index = checkboxes.indexOf(component);
            if (index != -1) {
                int i = 0;
                for (ComCheckbox checkbox : checkboxes) {
                    checkbox.isOver = i == index;
                    i++;
                }
            }
        }
    }

    /**
     * Called when the mouse releases the component
     *
     * @param component The component to be clicked
     * @param mouseX    X position of the mouse
     * @param mouseY    Y position of the mouse
     * @param button    Which button was clicked
     */
    @Override
    public void onMouseUp(BaseComponent component, int mouseX, int mouseY, int button) {

    }

    /**
     * Called when the mouse drags an item
     *
     * @param component The component to be clicked
     * @param mouseX    X position of the mouse
     * @param mouseY    Y position of the mouse
     * @param button    Which button was clicked
     * @param time      How long its been clicked
     */
    @Override
    public void onMouseDrag(BaseComponent component, int mouseX, int mouseY, int button, long time) {

    }

    public void selectFirst() {
        if (!checkboxes.isEmpty()) {
            ComCheckbox box = checkboxes.get(0);
            box.isOver = true;
            box.doAction(true);
        }
    }
}
