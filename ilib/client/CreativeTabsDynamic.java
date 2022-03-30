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
package ilib.client;

import ilib.api.client.FakeTab;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.GButton;
import ilib.gui.comp.GButtonNP;
import ilib.gui.comp.SimpleComponent;
import ilib.util.MCTexts;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.math.MathUtils;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/1/13 12:42
 */
public class CreativeTabsDynamic extends CreativeTabsMy {
    private class UpDownButton extends GButton {
        public UpDownButton(IGui parent, boolean up) {
            super(parent, -22, up ? 8 : 128, 96, 0, 14, 8);
        }

        @Override
        protected void doAction() {
            int nOff = MathUtils.clamp(offset + (yPos == 8 ? -1 : 1), 0,
                                       Math.max(categories.size() - 4, 0));
            if (nOff != offset) {
                offset = nOff;
                updateButton();
            }
        }
    }

    private class TabSelectButton extends GButtonNP {
        private CreativeTabs tab;

        public TabSelectButton(IGui parent, int yPos, CreativeTabs tab) {
            super(parent, -26, yPos, 22, 22);
            setDummy();
            setTab(tab);
        }

        protected void setTab(@Nonnull CreativeTabs tab) {
            this.tab = tab;
            setLabel(tab.getIcon());
            setToggled(selected.contains(tab));
        }

        @Override
        public void getDynamicTooltip(List<String> tooltip, int mouseX, int mouseY) {
            tooltip.add(MCTexts.format(tab.getTranslationKey()));
        }

        @Override
        protected void doAction() {
            if (selected.contains(tab)) {
                selected.remove(tab);
            } else {
                selected.add(tab);
            }
            owner.componentRequestUpdate();
        }
    }

    private class ScrollHandler extends SimpleComponent {
        public ScrollHandler(IGui parent) {
            super(parent, -26, 20, 24, 100);
        }

        @Override
        public void mouseScrolled(int x, int y, int dir) {
            if(!checkMouseOver(x, y)) return;

            int nOff = MathUtils.clamp(offset - dir, 0, Math.max(categories.size() - 4, 0));
            if (nOff != offset) {
                offset = nOff;
                updateButton();
            }
        }
    }

    @Override
    public boolean hasSearchBar() {
        for (CreativeTabs tabs : selected) {
            if (tabs.hasSearchBar()) return true;
        }
        return false;
    }

    public static final class Category extends CreativeTabsMy implements FakeTab {
        public Category(String name) {
            super(name);
        }

        public Category appendTo(CreativeTabsDynamic ct) {
            ct.categories.add(this);
            if (ct.selected.isEmpty()) {
                ct.selected.add(this);
            }
            return this;
        }
    }

    protected final List<CreativeTabs> categories;
    protected final Set<CreativeTabs> selected;
    protected int offset;

    protected List<Component> components;

    public CreativeTabsDynamic(String name) {
        super(name);
        this.categories = new SimpleList<>();
        this.selected = new MyHashSet<>();
    }

    @Override
    public void displayAllRelevantItems(NonNullList<ItemStack> list) {
        for (Item item : Item.REGISTRY) {
            if (selected.contains(item.getCreativeTab()))
                item.getSubItems(item.getCreativeTab(), list);
        }
    }

    public void addComponents(IGui parent, List<Component> list) {
        components = list;
        offset = MathUtils.clamp(offset, 0, Math.max(categories.size() - 4, 0));

        list.add(new UpDownButton(parent, true));
        list.add(new UpDownButton(parent, false));
        list.add(new ScrollHandler(parent));

        int yPos = 22;

        for (int i = offset, l = Math.min(categories.size(), offset + 4); i < l; i++) {
            CreativeTabs tabs = categories.get(i);
            list.add(new TabSelectButton(parent, yPos, tabs));

            yPos += 24;
        }

    }

    protected void updateButton() {
        for (int i = offset, j = 3, l = Math.min(categories.size(), offset + 4); i < l; i++, j++) {
            TabSelectButton button = (TabSelectButton) components.get(j);
            button.setTab(categories.get(i));
        }
    }
}
