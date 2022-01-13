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
import ilib.api.registry.IRegistry;
import ilib.api.registry.Localized;
import ilib.util.Hook;
import ilib.util.TextHelper;

import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 基于MI-Enumeration ({@link Propertied})创建自定翻译的Meta物品
 *
 * @param <T> The MI-Enumeration
 */
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:42
 */
public class ItemMetaNamed<T extends Localized> extends ItemMeta<T> {
    private String cache;
    private boolean needReplace;
    private String displayName;

    public ItemMetaNamed(String itemName, IRegistry<T> wrapper) {
        super(itemName, wrapper);
        if (ImpLib.isClient)
            addReload();
    }

    public ItemMetaNamed(String itemName, String textureLocation, IRegistry<T> wrapper) {
        super(itemName, textureLocation, wrapper);
        if (ImpLib.isClient)
            addReload();
    }

    private void addReload() {
        ImpLib.HOOK.add(Hook.LANGUAGE_RELOAD, ItemMetaNamed.this::initDisplayName);
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        if (displayName == null)
            initDisplayName();
        T t = getTypeByStack(stack);
        if (t == null) return TextHelper.translate("mi.invalid");
        if (needReplace)
            return this.displayName.replace("{}", t.getLocalizedName());
        else
            return t.getLocalizedName() + this.displayName;
    }

    void initDisplayName() {
        this.displayName = TextHelper.translate(cache);
        if (displayName.indexOf("{}") > 0) {
            needReplace = true;
        }
    }

    public void setName(String param) {
        this.cache = param;
        initDisplayName();
    }
}