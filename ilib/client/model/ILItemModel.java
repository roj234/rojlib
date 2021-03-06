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

import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class ILItemModel extends ModelInfo {
    private Item item;
    private int meta;
    @SideOnly(Side.CLIENT)
    private ModelResourceLocation model;

    private ILItemModel() {}

    @SideOnly(Side.CLIENT)
    private static ResourceLocation[] tmp;

    @Override
    @SideOnly(Side.CLIENT)
    public void apply() {
        ModelResourceLocation model = this.model;
        if (meta == 32767) {
            // ????????????????????????
            ModelLoader.setCustomMeshDefinition(item, new SingleTexture(model));
            // ?????????????????????(??????)??????
            if (tmp == null) tmp = new ResourceLocation[1];
            tmp[0] = model;
            ModelBakery.registerItemVariants(item, tmp);
        } else {
            ModelLoader.setCustomModelResourceLocation(item, meta, model);
        }
    }

    /**
     * ????????????????????????????????????,???????????????<ns>:textures/item/<path>.png
     */
    public static void Tex(Item item) {
        ResourceLocation rk = item.getRegistryName();
        Tex(item, 0, rk.getNamespace() + ":items/" + rk.getPath());
    }

    /**
     * ????????????????????????????????????,???????????????<ns>:textures/item/<path>.png
     */
    public static void Tex(Item item, int meta) {
        ResourceLocation rk = item.getRegistryName();
        Tex(item, meta, rk.getNamespace() + ":items/" + rk.getPath());
    }

    /**
     * ????????????????????????????????????,???????????????texture
     */
    public static void Tex(Item item, String texture) {
        Tex(item, 0, texture);
    }

    /**
     * ???<key>????????????????????????,???????????????<ns>:textures/item/<path>.png
     */
    public static void Tex(Item item, int meta, String texture) {
        if (!ImpLib.isClient) return;

        BlockStateBuilder imm = ImpLib.proxy.getItemMergedModel();

        ILItemModel info = new ILItemModel();
        info.item = item;
        info.meta = meta;

        ResourceLocation rk = item.getRegistryName();
        String typeId = Integer.toString(rk.hashCode() ^ meta, 36);
        if (imm.variants.getOrCreateMap("type").containsKey(typeId)) {
            typeId = rk.getNamespace() + '_' + rk.getPath() + '$' + meta;
        }
        if (texture.indexOf(':') < 0) {
            texture = rk.getNamespace() + ":items/" + texture;
        }
        imm.setVariantTexture("type", typeId, "layer0", texture);

        info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/items", "type=" + typeId);
        Registry.model(info);
    }

    /**
     * ?????????????????????????????????, ???????????????<key>
     */
    public static void MergedBlk(Item item) {
        MergedBlk(item, 0, item.getRegistryName().getPath());
    }

    /**
     * ?????????????????????????????????, ???????????????<key>
     */
    public static void MergedBlk(Item item, int meta) {
        MergedBlk(item, meta, item.getRegistryName().getPath());
    }

    /**
     * ?????????????????????????????????
     */
    public static void MergedBlk(Item item, int meta, String type) {
        if (!ImpLib.isClient) return;

        ILItemModel info = new ILItemModel();
        info.item = item;
        info.meta = meta;
        info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + type);
        Registry.model(info);
    }

    /**
     * ?????????????????????????????????
     */
    public static void Merged(Item item) {
        Merged(item, 0, item.getRegistryName().getPath());
    }

    /**
     * ?????????????????????????????????
     */
    public static void Merged(Item item, int meta) {
        Merged(item, meta, item.getRegistryName().getPath());
    }

    /**
     * ?????????????????????????????????
     */
    public static void Merged(Item item, int meta, String type) {
        if (!ImpLib.isClient) return;

        ILItemModel info = new ILItemModel();
        info.item = item;
        info.meta = meta;
        info.model = new ModelResourceLocation(ImpLib.MODID + ":generated/items", "type=" + type);
        Registry.model(info);
    }

    /**
     * ??????????????????????????????
     */
    @SideOnly(Side.CLIENT)
    public static void Model(Item item, ModelResourceLocation location) {
        Model(item, 0, location);
    }

    /**
     * ??????????????????????????????
     */
    @SideOnly(Side.CLIENT)
    public static void Model(Item item, int meta, ModelResourceLocation location) {
        if (!ImpLib.isClient) return;

        ILItemModel info = new ILItemModel();
        info.item = item;
        info.meta = meta;
        info.model = location;
        Registry.model(info);
    }

    /**
     * ??????'??????'???????????????
     */
    public static void Vanilla(Item item) {
        Vanilla(item, 0);
    }

    /**
     * ??????'??????'???????????????
     */
    public static void Vanilla(Item item, int meta) {
        if (!ImpLib.isClient) return;

        ILItemModel info = new ILItemModel();
        info.item = item;
        info.meta = meta;
        info.model = new ModelResourceLocation(item.getRegistryName(), "inventory");
        Registry.model(info);
    }
}
