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

import ilib.api.client.ICustomModel;
import ilib.api.registry.BlockPropTyped;
import ilib.api.registry.Propertied;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;

/**
 * ??????MI-Enumeration ({@link Propertied})??????Meta??????
 *
 * @param <T> The MI-Enumeration
 */
public class ItemMetaBlock<T extends Propertied<T>> extends ItemBlockMI implements ICustomModel {
    private final BlockPropTyped<T> prop;
    public final String name;
    private String propName;

    public ItemMetaBlock(Block block, BlockPropTyped<T> prop, String name, String propName) {
        super(block);
        setHasSubtypes(true);
        this.prop = prop;
        this.propName = propName;
        this.name = name;
    }

    public ItemMetaBlock(Block block) {
        this(block, block.getRegistryName().getPath(), true);
    }

    public ItemMetaBlock(Block block, String name) {
        this(block, name, true);
    }

    @SuppressWarnings("unchecked")
    public ItemMetaBlock(Block block, String name, boolean model) {
        super(block);
        setHasSubtypes(true);

        CharList tmp = IOUtil.getSharedCharBuf();
        BlockPropTyped<T> prop = null;
        Iterator<Map.Entry<IProperty<?>, Comparable<?>>> itr = block.getDefaultState()
                                                                    .getProperties().entrySet().iterator();

        // this map is sorted immutable map
        while (true) {
            Map.Entry<IProperty<?>, Comparable<?>> e = itr.next();
            IProperty<?> p = e.getKey();
            tmp.append(p.getName()).append('=');
            if (p instanceof BlockPropTyped) {
                if (prop != null) throw new IllegalStateException("Duplicate meta property: " + prop.getName() + " and " + p.getName());
                prop = (BlockPropTyped<T>) p;
                tmp.append("{name}");
            } else {
                tmp.append(p.getName(Helpers.cast(e.getValue())));
            }
            if (!itr.hasNext()) break;
            tmp.append(',');
        }

        this.prop = prop;
        this.propName = model ? tmp.toString() : null;
        this.name = name;
    }

    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing facing, float f1, float f2, float f3, IBlockState state) {
        T type = getTypeByStack(stack);
        if (type == null) return false;

        state = state.withProperty(prop, type);

        return super.placeBlockAt(stack, player, world, pos, facing, f1, f2, f3, state);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModel() {
        if (propName == null) return;

        ResourceLocation base = getTextureLocation();
        CharList tmp = IOUtil.getSharedCharBuf();
        for (T type : prop.getAllowedValues()) {
            if (indexFor(type) < 0) continue;
            String prop = tmp.append(propName).replace("{name}", type.getName()).toString();
            tmp.clear();
            ModelLoader.setCustomModelResourceLocation(this, indexFor(type), new ModelResourceLocation(base, prop));
        }
        propName = null;
    }

    protected ResourceLocation getTextureLocation() {
        return getRegistryName();
    }

    public int indexFor(T type) {
        return type.getIndex();
    }

    @Override
    protected void getSubItems(NonNullList<ItemStack> list) {
        for (T type : prop.getAllowedValues()) {
            list.add(new ItemStack(this, 1, indexFor(type)));
        }
    }

    @Nonnull
    @Override
    public String getTranslationKey(final ItemStack is) {
        T type = getTypeByStack(is);
        if (type == null) return "invalid";
        return "tile." + getRegistryName().getNamespace() + '.' + name + '.' + type.getName();
    }

    public T getTypeByStack(final ItemStack is) {
        return prop.byId(is.getItemDamage());
    }
}