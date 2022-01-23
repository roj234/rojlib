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
import ilib.api.registry.Propertied;
import ilib.util.Hook;

import net.minecraft.block.Block;
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

import javax.annotation.Nonnull;

/**
 * 基于MI-Enumeration ({@link Propertied})创建Meta方块
 *
 * @param <T> The MI-Enumeration
 */
/**
 * @author Roj234
 * @since 2021/6/2 23:56
 */
public class ItemMetaBlock<T extends Propertied<T>> extends ItemBlockMI {
    private final BlockPropTyped<T> prop;
    public final String name;

    public ItemMetaBlock(Block block, BlockPropTyped<T> prop, String name, String propName, String texName) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
        setNoRepair();
        ImpLib.HOOK.add(Hook.MODEL_REGISTER, () -> registerModel(propName, name));
        this.prop = prop;
        this.name = name;
    }

    public ItemMetaBlock(Block block, BlockPropTyped<T> prop, String name, String propName) {
        this(block, prop, name, propName, name);
    }

    public boolean placeBlockAt(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing facing, float f1, float f2, float f3, @Nonnull IBlockState state) {
        T type = getTypeByStack(stack);
        if (type == null) {
            return false;
        }

        state = state.withProperty(prop, type);

        return super.placeBlockAt(stack, player, world, pos, facing, f1, f2, f3, state);
    }

    public void registerModel(String propName, String name) {
        ResourceLocation baseLocation = new ResourceLocation("mi", name);
        for (T type : prop.getAllowedValues()) {
            if (indexFor(type) < 0)
                continue;
            String property = propName.replace("{name}", type.getName());
            ModelLoader.setCustomModelResourceLocation(this, indexFor(type),
                    new ModelResourceLocation(baseLocation, property));
        }
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
    public String getTranslationKey(@Nonnull final ItemStack is) {
        T type = getTypeByStack(is);
        if (type == null) {
            return "mi.invalid";
        }
        return "tile.mi." + name + "." + type.getName();
    }

    public T getTypeByStack(final ItemStack is) {
        return prop.byId(is.getItemDamage());
    }
}