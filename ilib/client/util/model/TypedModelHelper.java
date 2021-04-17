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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: BatteryPower.java
 */
package ilib.client.util.model;

import ilib.api.registry.IRegistry;
import ilib.api.registry.Indexable;
import ilib.api.registry.Propertied;
import ilib.client.resource.GeneratedModelRepo;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;

import java.util.Arrays;
import java.util.Collection;

public class TypedModelHelper {

    public static void itemTypedModel(Item item, ResourceLocation modelPath, String texturePath, IRegistry<?> wrapper) {
        itemTypedModel(item, modelPath, texturePath, wrapper.values());
    }

    public static void itemTypedModel(Item item, ResourceLocation modelPath, String texturePath, Indexable[] values) {
        generateModel(item, modelPath, texturePath, values, true);
    }

    public static void generateModel(Item item, ResourceLocation modelPath, String texturePath, Indexable[] values, boolean register) {
        BlockStateBuilder modelBuilder = new BlockStateBuilder(true).addVariant("type");

        String base = modelPath.getNamespace() + ':' + "items/" + texturePath + '/';

        for (Indexable t : values) {
            modelBuilder.setVariantTexture("type", t.getName(), base + t.getName());
            if (register) {
                ModelLoader.setCustomModelResourceLocation(item, t.getIndex(), new ModelResourceLocation(modelPath, "type" + '=' + t.getName()));
            }
        }

        GeneratedModelRepo.register("assets/" + modelPath.getNamespace() + "/blockstates/" + modelPath.getPath() + ".json", modelBuilder.build());
    }

    public static void generateModel(BlockStateBuilder modelBuilder, String texPos, Item item, ResourceLocation modelPath, String texturePath, Collection<String> values) {
        modelBuilder.addVariant("type");

        String base = modelPath.getNamespace() + ':' + "items/" + texturePath + '/';

        for (String t : values) {
            modelBuilder.setVariantTexture("type", t, texPos, base + t);
        }

        GeneratedModelRepo.register("assets/" + modelPath.getNamespace() + "/blockstates/" + modelPath.getPath() + ".json", modelBuilder.build());
    }

    public static void generateModel(Item item, ResourceLocation modelPath, String texturePath, Collection<String> values) {
        generateModel(new BlockStateBuilder(true), "layer0", item, modelPath, texturePath, values);
    }

    public static void generateModel(Item item, ResourceLocation modelPath, String texturePath, String[] values) {
        generateModel(item, modelPath, texturePath, Arrays.asList(values));
    }

    public static <T extends Propertied<T>> void itemLinearModel(Item item, ResourceLocation modelPath, String texturePath, int begin, int end) {
        BlockStateBuilder modelBuilder = new BlockStateBuilder(true);
        modelBuilder.addVariant("type");

        String base = modelPath.getNamespace() + ':' + "items/" + texturePath + '/';

        for (int i = begin; i < end; i++) {
            ModelLoader.setCustomModelResourceLocation(item, i, new ModelResourceLocation(modelPath, "type" + '=' + i));
            modelBuilder.setVariantTexture("type", String.valueOf(i), base + i);
        }

        GeneratedModelRepo.register("assets/" + modelPath.getNamespace() + "/blockstates/" + modelPath.getPath() + ".json", modelBuilder.build());
    }
}
