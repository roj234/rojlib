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
package ilib.client.model;

import ilib.ImpLib;
import ilib.Registry;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class ILBlockModel extends ModelInfo {
    private Block block;
    private ModelResourceLocation model;

    private ILBlockModel() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void apply() {
        ModelResourceLocation model = this.model;
        if (model != null) {
            ModelLoader.setCustomStateMapper(block, new SingleTexture(model));
            Item item = Item.getItemFromBlock(block);
            if (item != Items.AIR) {
                ModelLoader.setCustomModelResourceLocation(item, 0, model);
            }
        }
    }

    public static void Tex6(Block block, String texture) {
        if (!ImpLib.isClient) return;

        BlockStateBuilder imm = ImpLib.proxy.getBlockMergedModel();

        ILBlockModel info = new ILBlockModel();
        info.block = block;

        ResourceLocation rk = block.getRegistryName();
        String typeId = Integer.toString(rk.hashCode(), 36);

        if (imm.variants.getOrCreateMap("type").containsKey(typeId)) {
            typeId = rk.getNamespace() + "_" + rk.getPath();
        }
        if (texture.indexOf(':') < 0) {
            texture = rk.getNamespace() + ":blocks/" + texture;
        }
        imm.setVariantTexture("type", typeId, "all", texture);

        info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + typeId);
        Registry.model(info);
    }

    public static void MergedModel(Block block, String model) {
        if (!ImpLib.isClient) return;

        BlockStateBuilder imm = ImpLib.proxy.getBlockMergedModel();

        ILBlockModel info = new ILBlockModel();
        info.block = block;

        ResourceLocation rk = block.getRegistryName();
        String typeId = Integer.toString(rk.hashCode(), 36);

        if (imm.variants.getOrCreateMap("type").containsKey(typeId)) {
            typeId = rk.getNamespace() + "_" + rk.getPath();
        }
        if (model.indexOf(':') < 0) {
            model = rk.getNamespace() + ":" + model;
        }
        imm.setVariantModel("type", typeId, model);

        info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + typeId);
        Registry.model(info);
    }

    public static void Merged(Block block) {
        Merged(block, block.getRegistryName().getPath());
    }

    public static void Merged(Block block, String type) {
        if (!ImpLib.isClient) return;

        ILBlockModel info = new ILBlockModel();
        info.block = block;
        info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + type);
        Registry.model(info);
    }

    @SideOnly(Side.CLIENT)
    public static void Model(Block block, ModelResourceLocation location) {
        if (!ImpLib.isClient) return;

        ILBlockModel info = new ILBlockModel();
        info.block = block;
        info.model = location;
        Registry.model(info);
    }
}
