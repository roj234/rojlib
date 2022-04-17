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

import ilib.gui.GuiHelper;
import ilib.gui.IGui;
import roj.collect.SimpleList;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

import static ilib.gui.comp.GTab.FOLDED_SIZE;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class GTabs extends Component {
    public static final boolean LEFT = true, RIGHT = false;

    private final boolean reverse;

    private List<GTab> tabs = new SimpleList<>();
    private byte clicked;

    private GTab activeTab;

    public GTabs(IGui parent, int x, boolean reverse) {
        super(parent, x, 2);
        this.reverse = reverse;
    }

    public GTab addTab(List<Component> child, int w, int h, @Nullable ItemStack stack) {
        GTab tab = reverse ?
                new GReverseTab(owner, xPos + 5 - FOLDED_SIZE, yPos + tabs.size() * FOLDED_SIZE)
                : new GTab(owner, xPos - 5, yPos + tabs.size() * FOLDED_SIZE);

        tab.expandedWidth = w;
        tab.expandedHeight = h;
        tab.components = child;
        tab.stack = stack;
        tabs.add(tab);
        return tab;
    }

    @Override
    public void onInit() {
        super.onInit();

        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).onInit();
        }
    }

    private void alignVertical() {
        int y = yPos;
        for (int i = 0; i < tabs.size(); i++) {
            GTab tab = tabs.get(i);
            tab.setYPos(y);
            y += tab.getHeight();
        }
    }

    public final void getAreasCovered(int left, int top, List<Rectangle> areas) {
        Rectangle me = getArea(left, top);
        if (activeTab != null) {
            me.height += activeTab.height - FOLDED_SIZE;
            areas.add(activeTab.getArea(left, top));
        }
        areas.add(me);
    }

    /*******************************************************************************************************************
     * Component                                                                                                   *
     *******************************************************************************************************************/

    @Override
    public void mouseDown(int x, int y, int button) {
        super.mouseDown(x, y, button);

        for (int i = 0; i < tabs.size(); i++) {
            GTab tab = tabs.get(i);
            if (tab.isMouseOver(x, y)) {
                if (!tab.mouseDownActivated(x, y, button)) {
                    if (activeTab != tab) {
                        if (activeTab != null)
                            activeTab.setActive(false);
                        activeTab = tab;
                        activeTab.setActive(true);
                    } else if (tab.isActive()) {
                        activeTab.setActive(false);
                        activeTab = null;
                    } else {
                        activeTab.setActive(true);
                    }
                }
                if (activeTab != null) clicked |= 1<<button;
                break;
            }
        }
    }

    @Override
    public void mouseUp(int x, int y, int button) {
        super.mouseUp(x, y, button);

        if ((clicked & (1<<button)) != 0) {
            activeTab.mouseUp(x, y, button);
            clicked ^= 1<<button;
        }
    }

    @Override
    public void mouseDrag(int x, int y, int button, long time) {
        super.mouseDrag(x, y, button, time);

        if ((clicked & (1<<button)) != 0) {
            activeTab.mouseDrag(x, y, button, time);
        }
    }

    @Override
    public final void mouseScrolled(int x, int y, int dir) {
        super.mouseScrolled(x, y, dir);

        if (activeTab != null && activeTab.isMouseOver(x, y)) {
            activeTab.mouseScrolled(x, y, dir);
        }
    }

    @Override
    public final boolean isMouseOver(int x, int y) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).isMouseOver(x, y)) return true;
        }
        return false;
    }

    @Override
    public void keyTyped(char letter, int keyCode) {
        if (activeTab != null) activeTab.keyTyped(letter, keyCode);
    }

    @Override
    public void renderTooltip(int relX, int relY, int absX, int absY) {
        super.renderTooltip(relX, relY, absX, absY);

        for (int i = 0; i < tabs.size(); i++) {
            GTab tab = tabs.get(i);
            if (tab.isMouseOver(relX, relY)) {
                tab.renderTooltip(relX, relY, absX, absY);
                break;
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY) {
        alignVertical();
        GuiHelper.renderBackground(mouseX, mouseY, tabs);
    }

    @Override
    public void render2(int mouseX, int mouseY) {
        GuiHelper.renderForeground(mouseX, mouseY, tabs);
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    @Override
    public int getWidth() {
        return FOLDED_SIZE;
    }

    @Override
    public int getHeight() {
        return 5 + tabs.size() * FOLDED_SIZE;
    }

    public List<GTab> getTabs() {
        return tabs;
    }

    public void setTabs(List<GTab> tabs) {
        this.tabs = tabs;
    }
}
