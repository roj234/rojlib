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

import ilib.ClientProxy;
import ilib.api.client.FakeTab;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.gui.comp.control.ComButton;
import ilib.gui.comp.control.ComButtonOver;
import ilib.gui.comp.control.ComScrollBar;
import ilib.util.TextHelperM;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import roj.collect.MyHashSet;
import roj.math.MathUtils;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/13 12:42
 */
public class CreativeTabsDynamic extends CreativeTabsMy/* implements ISearchHandler*/ {
    private class UpDownButton extends ComButtonOver {
        public UpDownButton(IGui parent, boolean up) {
            super(parent, -24, up ? 8 : 120, up ? 96 : 111, 0, 15, 14);
        }

        @Override
        protected void doAction() {
            int nOff = MathUtils.clamp(offset + (this.u == 96 ? -1 : 1), 0, Math.max(categories.size() - 4, 0));
            if (nOff != offset) {
                offset = nOff;
                updateButton();
            }
            ((GuiContainerCreative.ContainerCreative) ((GuiContainer) getOwner()).inventorySlots).scrollTo(0);
        }
    }

    private class TabSelectButton extends ComButton {
        private CreativeTabs tab;

        public TabSelectButton(IGui parent, int yPos, CreativeTabs tab) {
            super(parent, -26, yPos, 127, 0, 22, 22);
            setTab(tab);
        }

        protected void setTab(@Nonnull CreativeTabs tab) {
            this.tab = tab;
            this.v = selected.contains(tab) ? 22 : 0;
        }

        @Override
        public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
            RenderHelper.enableGUIStandardItemLighting();
            ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI(tab.getIcon(), xPos + (width / 2) - 8, yPos + (height / 2) - 8);
            RenderHelper.disableStandardItemLighting();
        }

        @Override
        public void getDynamicToolTip(List<String> toolTip, int mouseX, int mouseY) {
            toolTip.add(TextHelperM.translate(tab.getTranslationKey()));
        }

        @Override
        protected void doAction() {
            boolean select = selected.contains(tab);
            if (select) {
                selected.remove(tab);
            } else {
                selected.add(tab);
            }
            owner.componentRequestUpdate();
        }
    }

    private class ScrollHandler extends ComScrollBar {
        public ScrollHandler(IGui parent) {
            super(parent, -10, 24, 8, 24 * 4);
        }

        @Override
        protected void onScroll(float position) {
        }

        @Override
        public void mouseScrolled(int x, int y, int dir) {
            if(!isMouseOver(x, y))
                return;
            int nOff = MathUtils.clamp(offset - dir, 0, Math.max(categories.size() - 4, 0));
            if (nOff != offset) {
                offset = nOff;
                updateButton();
            }
        }

        @Override
        public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
            drawTexturedModalRect(xPos, yPos, 0, 0, width, height);
        }
    }

    @Override
    public boolean hasSearchBar() {
        for (CreativeTabs tabs : selected) {
            if (tabs.hasSearchBar())
                return true;
        }
        return false;
    }

    @Nonnull
    @Override
    public ResourceLocation getBackgroundImage() {
        for (CreativeTabs tabs : selected) {
            if (tabs.hasSearchBar())
                return tabs.getBackgroundImage();
        }
        return super.getBackgroundImage();
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

    protected List<BaseComponent> components;

    public CreativeTabsDynamic(String name) {
        super(name);
        this.categories = new LinkedList<>();
        this.selected = new MyHashSet<>(2);
    }

    @Override
    public void displayAllRelevantItems(NonNullList<ItemStack> list) {
        for (Item item : Item.REGISTRY) {
            if (selected.contains(item.getCreativeTab()))
                item.getSubItems(item.getCreativeTab(), list);
        }
    }

    public void addComponents(IGui parent, List<BaseComponent> list) {
        components = list;
        offset = MathUtils.clamp(offset, 0, Math.max(categories.size() - 4, 0));

        list.add(new UpDownButton(parent, true));

        int yPos = 24;

        for (int i = offset, l = Math.min(categories.size(), offset + 4); i < l; i++) {
            CreativeTabs tabs = categories.get(i);
            list.add(new TabSelectButton(parent, yPos, tabs));

            yPos += 24;
        }

        list.add(new UpDownButton(parent, false));

        if (categories.size() > 4) {
            list.add(new ScrollHandler(parent));
        }
    }


    protected void updateButton() {
        for (int i = offset, j = 1, l = Math.min(categories.size(), offset + 4); i < l; i++, j++) {
            TabSelectButton button = (TabSelectButton) components.get(j);
            button.setTab(categories.get(i));
        }
    }
}
