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
package ilib.asm.nixim.client;

import ilib.Config;
import ilib.asm.util.MCHooks;
import ilib.client.CreativeTabsDynamic;
import ilib.client.api.IContainerCreative;
import ilib.client.api.ISearchHandler;
import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.util.PinyinUtil;
import org.lwjgl.input.Mouse;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.FilterList;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/12 15:11
 */

@Nixim(value = "net.minecraft.client.gui.inventory.GuiContainerCreative", copyItf = true)
//!!AT ["net.minecraft.client.gui.inventory.GuiContainerCreative", ["func_147050_b"], true]
abstract class CustomCreativeTab extends GuiContainerCreative implements IGui, IContainerCreative {
    @Shadow("field_147058_w")
    static int selectedTabIndex;

    @Shadow("field_147006_u")
    Slot hoveredSlot;

    @Shadow("field_147067_x")
    private float currentScroll;

    @Shadow("field_147062_A")
    private GuiTextField searchField;

    @Copy
    static List<BaseComponent> components;

    public CustomCreativeTab(EntityPlayer player) {
        super(player);
    }

    public void setScrollPos(float pos) {
        this.currentScroll = pos;
        ((GuiContainerCreative.ContainerCreative) inventorySlots).scrollTo(pos);
    }

    @Nonnull
    @Override
    @Copy
    public FontRenderer getFontRenderer() {
        return fontRenderer;
    }

    @Copy
    public TileEntity getTileEntity() {
        return null;
    }

    @Override
    @Copy
    public ResourceLocation getTexture() {
        return BaseComponent.COMPONENTS_TEXTURE;
    }

    @Override
    @Inject("func_73864_a")
    protected void mouseClicked(int mouseX1, int mouseY1, int mouseButton) throws IOException {
        super.mouseClicked(mouseX1, mouseY1, mouseButton);
        if (components == null) return;
        final int mouseX = mouseX1 - guiLeft;
        final int mouseY = mouseY1 - guiTop;
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX, mouseY)) {
                component.mouseDown(mouseX, mouseY, mouseButton);
            }
        }
    }

    @Override
    @Inject("func_146286_b")
    protected void mouseReleased(int mouseX1, int mouseY1, int state) {
        super.mouseReleased(mouseX1, mouseY1, state);
        if (components == null) return;
        final int mouseX = mouseX1 - guiLeft;
        final int mouseY = mouseY1 - guiTop;
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX, mouseY)) {
                component.mouseUp(mouseX, mouseY, state);
            }
        }
    }

    @Override
    @Copy(newName = "func_146273_a")
    protected void mouseClickMove(int mouseX1, int mouseY1, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX1, mouseY1, clickedMouseButton, timeSinceLastClick);
        if (components == null) return;
        final int mouseX = mouseX1 - guiLeft;
        final int mouseY = mouseY1 - guiTop;
        for (BaseComponent component : components) {
            if (component.isMouseOver(mouseX, mouseY)) {
                component.mouseDrag(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            }
        }
    }

    @Override
    @Inject("func_146274_d")
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (components == null) return;
        int scrollDirection = Mouse.getEventDWheel();
        if (scrollDirection != 0) {
            int x = Mouse.getX(), y = Mouse.getY();
            for (BaseComponent component : components) {
                component.mouseScrolled(x, y, scrollDirection > 0 ? 1 : -1);
            }
        }
    }

    @Override
    @Inject("func_73869_a")
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        if (components == null) return;
        for (BaseComponent component : components) {
            component.keyTyped(typedChar, keyCode);
        }
    }

    @Inject("func_146979_b")
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        //GlStateManager.pushMatrix();
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        //GlStateManager.popMatrix();
        if (components == null) return;
        //GlStateManager.pushMatrix();

        RenderUtils.bindTexture(BaseComponent.COMPONENTS_TEXTURE);
        for (BaseComponent component : components) {
            RenderUtils.prepareRenderState();
            component.renderOverlay(guiLeft, guiTop, mouseX - guiLeft, mouseY - guiTop);
            RenderUtils.restoreRenderState();
        }

        //GlStateManager.popMatrix();
    }

    @Inject("func_146976_a")
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        //GlStateManager.pushMatrix();
        super.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
        //GlStateManager.popMatrix();
        if (components == null) return;
        GlStateManager.pushMatrix();

        RenderUtils.prepareRenderState();

        GlStateManager.translate(guiLeft, guiTop, 0);
        RenderUtils.bindTexture(BaseComponent.COMPONENTS_TEXTURE);

        for (BaseComponent component : components) {
            GlStateManager.pushMatrix();
            component.render(guiLeft, guiTop, mouseX - guiLeft, mouseY - guiTop);
            GlStateManager.popMatrix();
        }

        //RenderUtils.restoreRenderState();
        //RenderUtils.restoreColor();

        GlStateManager.popMatrix();
    }

    @Inject("func_147050_b")
    public void setCurrentCreativeTab(CreativeTabs tab) {
        super.setCurrentCreativeTab(tab);

        if (tab != CreativeTabs.SEARCH) {
            //this.searchField.setFocused(true);
            this.searchField.setCanLoseFocus(true);
        }

        if (tab instanceof CreativeTabsDynamic) {
            components = components == null ? new ArrayList<>() : new ArrayList<>(components.size());
            ((CreativeTabsDynamic) tab).addComponents(this, components);
        } else {
            if (components != null)
                components = null;
        }
    }

    @Copy(newName = "func_191948_b")
    protected void drawHoveredTooltip(int mouseX, int mouseY) {
        if (this.mc.player.inventory.getItemStack().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.getHasStack()) {
            this.renderToolTip(this.hoveredSlot.getStack(), mouseX, mouseY);
        } else {
            if (components == null) return;
            for (BaseComponent component : components) {
                if (component.isMouseOver(mouseX - guiLeft, mouseY - guiTop)) component.renderToolTip(mouseX, mouseY);
            }
        }
    }

    @Inject("func_147053_i")
    private void updateCreativeSearch() {
        GuiContainerCreative.ContainerCreative container = (GuiContainerCreative.ContainerCreative) this.inventorySlots;
        final NonNullList<ItemStack> list = container.itemList;

        list.clear();

        CreativeTabs tab = CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex];

        final String text = this.searchField.getText().toLowerCase();

        if (tab != CreativeTabs.SEARCH) {
            if (tab instanceof ISearchHandler) {
                ((ISearchHandler) tab).search(list, text);
            } else {
                if (!text.isEmpty()) {
                    NonNullList<ItemStack> checker = new NonNullList<>(new FilterList<>((old, latest) -> {
                        boolean matches = false;

                        for (String line : MCHooks.makeBetterInformation(latest)) {
                            if (containsPinyin(line.toLowerCase(), text)) {
                                matches = true;
                                break;
                            }
                        }

                        if (matches) {
                            list.add(latest);
                        }

                        return false;
                    }), null);

                    tab.displayAllRelevantItems(checker);
                } else {
                    tab.displayAllRelevantItems(list);
                }
            }
        } else {
            if (text.isEmpty()) {
                for (Item item : Item.REGISTRY) {
                    item.getSubItems(CreativeTabs.SEARCH, list);
                }
            } else {
                list.addAll(this.mc.getSearchTree(SearchTreeManager.ITEMS).search(text));
            }
        }

        this.currentScroll = 0.0F;
        container.scrollTo(0.0F);
    }

    @Copy
    private boolean containsPinyin(String line, String text) {
        if (!Config.enablePinyinSearch)
            return line.contains(text);
        return line.contains(text) || PinyinUtil.pinyin().toPinyin(line).contains(text);
    }

    @Override
    @Copy
    public void componentRequestUpdate() {
        setCurrentCreativeTab(CreativeTabs.CREATIVE_TAB_ARRAY[getSelectedTabIndex()]);
    }

    @Override
    @Copy
    public int getGuiLeft() {
        return guiLeft;
    }

    @Override
    @Copy
    public int getGuiTop() {
        return guiTop;
    }

    @Override
    @Copy
    public int getXSize() {
        return xSize;
    }

    @Override
    @Copy
    public int getYSize() {
        return ySize;
    }

    @Copy
    public int getImgXSize() {
        return 256;
    }

    @Copy
    public int getImgYSize() {
        return 256;
    }
}
