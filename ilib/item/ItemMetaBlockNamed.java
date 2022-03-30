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

package ilib.item;

import ilib.ImpLib;
import ilib.api.registry.BlockPropTyped;
import ilib.api.registry.Localized;
import ilib.api.registry.Propertied;
import ilib.util.Hook;
import ilib.util.MCTexts;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 基于MI-Enumeration ({@link Propertied})创建自定翻译的Meta方块
 *
 * @param <T> The MI-Enumeration
 */
/**
 * @author Roj234
 * @since 2021/6/2 23:42
 */
public class ItemMetaBlockNamed<T extends Propertied<T>> extends ItemMetaBlock<T> {
    private String cache;
    private boolean needReplace;
    private String displayName;

    public ItemMetaBlockNamed(Block block, BlockPropTyped<T> prop, String name, String propName) {
        super(block, prop, name, propName);
        ImpLib.HOOK.add(Hook.LANGUAGE_RELOAD, ItemMetaBlockNamed.this::initDisplayName);
        this.cache = "tile.mi." + name;
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        if (displayName == null)
            initDisplayName();
        Localized t = (Localized) getTypeByStack(stack);
        if (t == null) return MCTexts.format("mi.invalid");
        if (needReplace)
            return this.displayName.replace("{}", t.getLocalizedName());
        else
            return t.getLocalizedName() + this.displayName;
    }

    public void initDisplayName() {
        this.displayName = MCTexts.format(cache);
        if (displayName.indexOf("{}") > 0) {
            needReplace = true;
        }
    }

    public void setName(String param) {
        this.cache = param;
        initDisplayName();
    }
}