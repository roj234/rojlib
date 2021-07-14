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
package ilib.autoreg;

import ilib.Config;
import ilib.Registry;
import ilib.client.register.BlockModelInfo;
import ilib.client.register.ItemModelInfo;
import ilib.util.ForgeUtil;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51java
 */
public class AutoRegHandler {
    static Map<String, Object> holder = new HashMap<>();

    public static void registerTileEntity(Class<? extends TileEntity> tileClass, String str) {
        if (str.equals("")) {
            str = tileClass.getSimpleName().toLowerCase();
            if (str.startsWith("tileentity"))
                str = str.substring(10);
            else if (str.startsWith("tile"))
                str = str.substring(4);
            str = ForgeUtil.getCurrentModId() + ':' + str;
        }
        TileEntity.register(str, tileClass);
    }

    @Nonnull
    public static Object get(String key) {
        return holder.get(ForgeUtil.getCurrentModId() + '-' + key);
    }

    public static void registerBlock(Block block, String regName, Item item, String modelType, String modelPath) {
        if (!Config.registerItem && regName.startsWith("ilib:")) return;

        ResourceLocation loc = new ResourceLocation(regName);

        String modid = loc.getNamespace().equals("minecraft") ? ForgeUtil.getCurrentModId() : loc.getNamespace();

        holder.put(modid + ":B:" + loc.getPath(), block);

        int type = 0;
        switch (modelType) {
            case "DEFAULT":
                type = 0;
                break;
            case "CUSTOM":
                type = 2;
                break;
            case "MERGED":
                type = 1;
                break;
        }

        Registry.blocks().add(block.setRegistryName(modid, loc.getPath()).setTranslationKey(modid + '.' + loc.getPath()));

        if (modelPath == null) {
            Registry.model(new BlockModelInfo(block, type == 1));
        } else {
            Registry.model(new BlockModelInfo(block, new ModelResourceLocation(modelPath)));
        }

        if (item != null)
            registerItem(modid, loc.getPath(), item, type, modelPath);
    }

    public static void registerItem(Item item, String regName, String modelType, String modelPath) {
        if (!Config.registerItem && regName.startsWith("ilib:")) return;

        ResourceLocation loc = new ResourceLocation(regName);

        String modid = loc.getNamespace().equals("minecraft") ? ForgeUtil.getCurrentModId() : loc.getNamespace();

        holder.put(modid + ":I:" + loc.getPath(), item);

        int type = 0;
        switch (modelType) {
            case "DEFAULT":
                type = 0;
                break;
            case "CUSTOM":
                type = 2;
                break;
            case "MERGED":
                type = 1;
                break;
        }
        registerItem(modid, loc.getPath(), item, type, modelPath);
    }

    private static void registerItem(String modid, String id, Item item, int model, String modelPath) {
        Registry.items().add(item.setRegistryName(modid, id).setTranslationKey(modid + '.' + id));

        if (modelPath == null) {
            Registry.model(new ItemModelInfo(item, model == 1));
        } else {
            Registry.model(new ItemModelInfo(item, new ModelResourceLocation(modelPath)));
        }
    }
}
