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

import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class GTabs extends Component {
    // Variables
    protected List<GTab> tabs;
    protected GTab       activeTab;

    public GTabs(IGui parentGui, int x) {
        super(parentGui, x, 2);
        tabs = new ArrayList<>();
        setListener(new TabMouseListener());
    }

    public final GTab addTab(List<Component> components, int maxWidth, int maxHeight, int textureU, int textureV, @Nullable ItemStack stack) {
        GTab tab = new GTab(owner, xPos - 5, yPos + (yPos + (tabs.size()) * 24),
                            textureU, textureV, maxWidth, maxHeight, stack);

        for (Component com : components) {
            tab.addChild(com);
        }

        tabs.add(tab);
        return tab;
    }

    public final GTab addReverseTab(List<Component> components, int maxWidth, int maxHeight, int textureU, int textureV, @Nullable ItemStack stack) {
        GTab tab = new GReverseTab(owner, xPos + 5, yPos + (yPos + (tabs.size()) * 24),
                                   textureU, textureV, maxWidth, maxHeight, stack);
        for (Component com : components) {
            tab.addChild(com);
        }
        tabs.add(tab);
        return tab;
    }

    /**
     * Move the tabs to fit the expansion of one
     */
    private void realignTabsVertically() {
        int y = yPos;
        for (GTab tab : tabs) {
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
        for (GTab guiTab : tabs) {
            if (guiTab instanceof GReverseTab)
                list.add(new Rectangle(guiLeft + guiTab.getXPos() - getWidth(), guiTop + guiTab.getYPos(),
                        guiTab.getWidth(), guiTab.getHeight()));
            else
                list.add(new Rectangle(guiLeft + guiTab.getXPos(), guiTop + guiTab.getYPos(),
                        guiTab.getWidth(), guiTab.getHeight()));
        }
        return list;
    }

    /*******************************************************************************************************************
     * Component                                                                                                   *
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
        for (GTab guiTab : tabs) {
            guiTab.mouseScrolledTab(x, y, dir);
        }
    }

    @Override
    public final boolean isMouseOver(int mouseX, int mouseY) {
        for (GTab tab : tabs) {
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
        for (GTab guiTab : tabs) {
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
        for (GTab guiTab : tabs) {
            if (guiTab.isMouseOver(mouseX - owner.getLeft(), mouseY - owner.getTop()))
                guiTab.renderToolTip(mouseX, mouseY);
        }
    }

    /**
     * Called to render the component
     */
    @Override
    public final void render(int mouseX, int mouseY) {
        realignTabsVertically();
        for (GTab tab : tabs) {
            GlStateManager.pushMatrix();
            RenderUtils.prepareRenderState();
            GlStateManager.translate(tab.getXPos(), tab.getYPos(), 0);
            tab.render(mouseX - tab.getXPos(), mouseY - tab.getYPos());
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
    public final void renderOverlay(int mouseX, int mouseY) {
        for (GTab tab : tabs) {
            GlStateManager.pushMatrix();
            RenderUtils.prepareRenderState();
            GlStateManager.translate(tab.getXPos(), tab.getYPos(), 0);
            tab.renderOverlay(mouseX, mouseY);
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

    public final List<GTab> getTabs() {
        return tabs;
    }

    public final void setTabs(List<GTab> tabs) {
        this.tabs = tabs;
    }

    /**
     * Private class to hold all mouse event logic
     */
    private class TabMouseListener implements ComponentListener {

        /**
         * Called when the mouse clicks on the component
         *
         * @param com The component to be clicked
         * @param mouseX    X position of the mouse
         * @param mouseY    Y position of the mouse
         * @param button    Which button was clicked
         */
        @Override
        public final void mouseDown(Component com, int mouseX, int mouseY, int button) {
            for (int i = 0; i < tabs.size(); i++) {
                GTab tab = tabs.get(i);
                if (tab.isMouseOver(mouseX, mouseY)) {
                    if (tab.getListener() == null) {
                        if (!tab.mouseDownActivated(
                                (tab instanceof GReverseTab) ? mouseX + tab.expandedWidth - 5 : mouseX - owner.getWidth() + 5,
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
         * @param com The component to be clicked
         * @param mouseX    X position of the mouse
         * @param mouseY    Y position of the mouse
         * @param button    Which button was clicked
         */
        @Override
        public final void mouseUp(Component com, int mouseX, int mouseY, int button) {
            for (int i = 0; i < tabs.size(); i++) {
                GTab tab = tabs.get(i);
                if (tab.isMouseOver(mouseX, mouseY)) {
                    tab.mouseUpActivated((tab instanceof GReverseTab) ? mouseX + tab.expandedWidth - 5 : mouseX - owner.getWidth() + 5,
                            mouseY - (i * 24) - 2, button);
                    return;
                }
            }
        }

        /**
         * Called when the mouse drags an item
         *
         * @param com The component to be clicked
         * @param mouseX    X position of the mouse
         * @param mouseY    Y position of the mouse
         * @param button    Which button was clicked
         * @param time      How long its been clicked
         */
        @Override
        public final void mouseDrag(Component com, int mouseX, int mouseY, int button, long time) {
            for (int i = 0; i < tabs.size(); i++) {
                GTab tab = tabs.get(i);
                if (tab.isMouseOver(mouseX, mouseY)) {
                    tab.mouseDragActivated((tab instanceof GReverseTab) ? mouseX + tab.expandedWidth - 5 : mouseX - owner.getWidth() + 5,
                            mouseY - (i * 24) - 2, button, time);
                    return;
                }
            }
        }
    }
}
