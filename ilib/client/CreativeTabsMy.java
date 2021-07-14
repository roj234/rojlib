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

import ilib.Config;
import ilib.ImpLib;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class CreativeTabsMy extends CreativeTabs {
    private ItemStack iconStack = ItemStack.EMPTY;
    private boolean search;

    public static CreativeTabsMy tabBlockOnly;

    public static void preInit() {
        if (Config.enableMissingItemCreation)
            tabBlockOnly = new CreativeTabsMy("ilib.missing").setBackground("ilib").setIcon(new ItemStack(Blocks.BEDROCK));
    }

    public CreativeTabsMy(String name) {
        super(name);
    }

    public CreativeTabsMy setBackground(String bg) {
        int index = bg.indexOf(":");
        this.setBackgroundImageName(index != -1 ? bg.substring(0, index) + ":" + "textures/gui/creative_tab/" + bg.substring(index + 1) + ".png" : ImpLib.MODID + ":" + "textures/gui/creative_tab/" + bg + ".png");
        return this;
    }

    public CreativeTabsMy setIcon(ItemStack stack) {
        this.iconStack = stack;
        return this;
    }

    public CreativeTabsMy setSearchable() {
        this.search = true;
        return this;
    }

    @Override
    public boolean hasSearchBar() {
        return search;
    }

    @Nonnull
    @SideOnly(Side.CLIENT)
    @Override
    public ResourceLocation getBackgroundImage() {
        return new ResourceLocation(getBackgroundImageName());
    }

    @Nonnull
    @Override
    public ItemStack getIcon() {
        return iconStack;
    }

    @Override
    public ItemStack createIcon() {
        return iconStack;
    }
}