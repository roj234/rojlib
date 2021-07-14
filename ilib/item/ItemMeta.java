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
import ilib.api.registry.Indexable;
import ilib.api.registry.Propertied;
import ilib.api.registry.RegistryBuilder;
import ilib.client.util.model.TypedModelHelper;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;

import javax.annotation.Nonnull;

/**
 * 基于MI-Enumeration ({@link Propertied})创建Meta物品
 *
 * @param <T> The MI-Enumeration
 * @see RegistryBuilder
 *//**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:42
 */
public class ItemMeta<T extends Indexable> extends ItemBase {
    public final IRegistry<T> wrapper;
    public final String name;
    private Object generateModel = true;

    /**
     * 基于MI-Enumeration ({@link Propertied})创建不包含材质的Meta物品
     *
     * @param name    Registry name
     * @param wrapper The MI-Enumeration's wrapper
     * @see RegistryBuilder
     */
    public ItemMeta(String name, IRegistry<T> wrapper) {
        super();
        setHasSubtypes(true);
        setMaxDamage(0);
        //奇妙的“修复物品的设备”
        setNoRepair();

        this.name = name;
        this.wrapper = wrapper;
    }

    /**
     * 基于MI-Enumeration ({@link Propertied})创建不包含材质的Meta物品
     *
     * @param name        Registry name
     * @param texturePath Texture path
     * @param wrapper     The MI-Enumeration's wrapper
     * @see RegistryBuilder
     */
    public ItemMeta(String name, String texturePath, IRegistry<T> wrapper) {
        this(name, wrapper);
        ImpLib.HOOK.add(ilib.util.hook.Hook.MODEL_REGISTER, () -> registerModel(texturePath));
    }

    /**
     * 使用{@link RegistryBuilder}创建一个标准meta物品
     *
     * @param name Register and texture name
     * @param list item names
     */
    public static ItemMeta<RegistryBuilder.Std> standard(String name, String texture, String... list) {
        return new ItemMeta<>(name, texture, new RegistryBuilder(list).build());
    }

    public ItemMeta<T> setGenerateModel(Object generateModel) {
        this.generateModel = generateModel;
        return this;
    }

    public void registerModel(String texturePath) {
        if (generateModel == Boolean.TRUE) {
            TypedModelHelper.itemTypedModel(this, new ResourceLocation(modid(), "item/" + name), texturePath, wrapper);
        } else if (generateModel == Boolean.FALSE) {
            ResourceLocation base = new ResourceLocation(modid(), "item/" + texturePath);
            for (T t : wrapper.values()) {
                ModelLoader.setCustomModelResourceLocation(this, indexFor(t), new ModelResourceLocation(base, "type=" + t.getName()));
            }
        } else {
            ModelResourceLocation loc = new ModelResourceLocation((String) generateModel);
            for (T t : wrapper.values()) {
                ModelLoader.setCustomModelResourceLocation(this, indexFor(t), loc);
            }
        }
    }

    protected int indexFor(T t) {
        return t.getIndex();
    }

    @Nonnull
    @Override
    public final String getTranslationKey(@Nonnull final ItemStack is) {
        return "item.mi." + name + "." + this.nameOf(is);
    }

    @Override
    public void getSubItems(NonNullList<ItemStack> list) {
        for (T type : wrapper.values()) {
            list.add(new ItemStack(this, 1, indexFor(type)));
        }
    }

    public final ItemStack getStackByType(final T type, final int count) {
        return new ItemStack(this, count, indexFor(type));
    }

    public T getTypeByStack(final ItemStack is) {
        final int meta = is.getItemDamage();
        return wrapper.byId(meta);
    }

    protected final String nameOf(final ItemStack is) {
        if (is.isEmpty()) {
            return "invalid";
        }
        T t = getTypeByStack(is);
        return t == null ? "invalid" : t.getName();
    }

}