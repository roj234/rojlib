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

import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.gui.comp.IGuiListener;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class ComTabs extends BaseComponent {
    // Variables
    protected List<ComTab> tabs;
    protected ComTab activeTab;

    public ComTabs(IGui parentGui, int x) {
        super(parentGui, x, 2);
        tabs = new ArrayList<>();
        setGuiListener(new TabMouseListener());
    }

    public final ComTab addTab(List<BaseComponent> components, int maxWidth, int maxHeight, int textureU, int textureV, @Nullable ItemStack stack) {
        ComTab tab = new ComTab(owner, xPos - 5, yPos + (yPos + (tabs.size()) * 24),
                textureU, textureV, maxWidth, maxHeight, stack);

        for (BaseComponent component : components) {
            tab.addChild(component);
        }

        tabs.add(tab);
        return tab;
    }

    public final ComTab addReverseTab(List<BaseComponent> components, int maxWidth, int maxHeight, int textureU, int textureV, @Nullable ItemStack stack) {
        ComTab tab = new ComReverseTab(owner, xPos + 5, yPos + (yPos + (tabs.size()) * 24),
                textureU, textureV, maxWidth, maxHeight, stack);
        for (BaseComponent component : components) {
            tab.addChild(component);
        }
        tabs.add(tab);
        return tab;
    }

    /**
     * Move the tabs to fit the expansion of one
     */
    private void realignTabsVertically() {
        int y = yPos;
        for (ComTab tab : tabs) {
            tab.setYPos(y);
            y += tab.getHeight();
        }
    }

    /**
     * Gets the areas covered by the tab collection
     *
     * @param guiLeft The gui left of the parent
     * @param guiTop  The gui top of the parent
     * @return A list of covered areas
     */
    public final List<Rectangle> getAreasCovered(int guiLeft, int guiTop) {
        List<Rectangle> list = new ArrayList<>();
        for (ComTab guiTab : tabs) {
            if (guiTab instanceof ComReverseTab)
                list.add(new Rectangle(guiLeft + guiTab.getXPos() - getWidth(), guiTop + guiTab.getYPos(),
                        guiTab.getWidth(), guiTab.getHeight()));
            else
                list.add(new Rectangle(guiLeft + guiTab.getXPos(), guiTop + guiTab.getYPos(),
                        guiTab.getWidth(), guiTab.getHeight()));
        }
        return list;
    }

    /*******************************************************************************************************************
     * BaseComponent                                                                                                   *
     *******************************************************************************************************************/

    /**
     * Called when the mouse is scrolled
     *
     * @param dir 1 for positive, -1 for negative
     */
    @Override
    public final void mouseScrolled(int x, int y, int dir) {
        if(!isMouseOver(x, y))
            return;
        for (ComTab guiTab : tabs) {
            guiTab.mouseScrolledTab(x, y, dir);
        }
    }

    @Override
    public final boolean isMouseOver(int mouseX, int mouseY) {
        for (ComTab tab : tabs) {
            if (tab.isMouseOver(mouseX, mouseY))
                return true;
        }
        return false;
    }

    /**
     * Used when a key is pressed
     *
     * @param letter  The letter
     * @param keyCode The code
     */
    @Override
    public final void keyTyped(char letter, int keyCode) {
        for (ComTab guiTab : tabs) {
            guiTab.keyTyped(letter, keyCode);
        }
    }

    /**
     * Render the tooltip if you can
     *
     * @param mouseX Mouse X
     * @param mouseY Mouse Y
     */
    @Override
    public final void renderToolTip(int mouseX, int mouseY) {
        for (ComTab guiTab : tabs) {
            if (guiTab.isMouseOver(mouseX - owner.getGuiLeft(), mouseY - owner.getGuiTop()))
                guiTab.renderToolTip(mouseX, mouseY);
        }
    }

    /**
     * Called to render the component
     */
    @Override
    public final void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        realignTabsVertically();
        for (ComTab tab : tabs) {
            GlStateManager.pushMatrix();
            RenderUtils.prepareRenderState();
            GlStateManager.translate(tab.getXPos(), tab.getYPos(), 0);
            tab.render(0, 0, mouseX - tab.getXPos(), mouseY - tab.getYPos());
            tab.moveSlots();
            RenderUtils.restoreRenderState();
            RenderUtils.restoreColor();
            GlStateManager.popMatrix();
        }
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public final void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        for (ComTab tab : tabs) {
            GlStateManager.pushMatrix();
            RenderUtils.prepareRenderState();
            GlStateManager.translate(tab.getXPos(), tab.getYPos(), 0);
            tab.renderOverlay(0, 0, mouseX, mouseY);
            RenderUtils.restoreRenderState();
            RenderUtils.restoreColor();
            GlStateManager.popMatrix();
        }
    }

    /**
     * Used to find how wide this is
     *
     * @return How wide the component is
     */
    @Override
    public final int getWidth() {
        return 24;
    }

    /**
     * Used to find how tall this is
     *
     * @return How tall the component is
     */
    @Override
    public final int getHeight() {
        return 5 + (tabs.size() * 24);
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public final List<ComTab> getTabs() {
        return tabs;
    }

    public final void setTabs(List<ComTab> tabs) {
        this.tabs = tabs;
    }

    /**
     * Private class to hold all mouse event logic
     */
    private class TabMouseListener implements IGuiListener {

        /**
         * Called when the mouse clicks on the component
         *
         * @param component The component to be clicked
         * @param mouseX    X position of the mouse
         * @param mouseY    Y position of the mouse
         * @param button    Which button was clicked
         */
        @Override
        public final void onMouseDown(BaseComponent component, int mouseX, int mouseY, int button) {
            for (int i = 0; i < tabs.size(); i++) {
                ComTab tab = tabs.get(i);
                if (tab.isMouseOver(mouseX, mouseY)) {
                    if (tab.getGuiListener() == null) {
                        if (!tab.mouseDownActivated(
                                (tab instanceof ComReverseTab) ? mouseX + tab.expandedWidth - 5 : mouseX - owner.getXSize() + 5,
                                mouseY - (i * 24) - 2, button)) {
                            if (activeTab != tab) {
                                if (activeTab != null)
                                    activeTab.setActive(false);
                                activeTab = tab;
                                activeTab.setActive(true);
                                return;
                            } else if (tab.areChildrenActive()) {
                                activeTab.setActive(false);
                                activeTab = null;
                                return;
                            } else {
                                activeTab.setActive(true);
                                return;
                            }
                        }
                    } else
                        tab.mouseDown(mouseX, mouseY, button);
                    return;
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
        public final void onMouseUp(BaseComponent component, int mouseX, int mouseY, int button) {
            for (int i = 0; i < tabs.size(); i++) {
                ComTab tab = tabs.get(i);
                if (tab.isMouseOver(mouseX, mouseY)) {
                    tab.mouseUpActivated((tab instanceof ComReverseTab) ? mouseX + tab.expandedWidth - 5 : mouseX - owner.getXSize() + 5,
                            mouseY - (i * 24) - 2, button);
                    return;
                }
            }
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
        public final void onMouseDrag(BaseComponent component, int mouseX, int mouseY, int button, long time) {
            for (int i = 0; i < tabs.size(); i++) {
                ComTab tab = tabs.get(i);
                if (tab.isMouseOver(mouseX, mouseY)) {
                    tab.mouseDragActivated((tab instanceof ComReverseTab) ? mouseX + tab.expandedWidth - 5 : mouseX - owner.getXSize() + 5,
                            mouseY - (i * 24) - 2, button, time);
                    return;
                }
            }
        }
    }
}
