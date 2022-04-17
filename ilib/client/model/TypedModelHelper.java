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
package ilib.client.model;

import ilib.api.registry.IRegistry;
import ilib.api.registry.Indexable;
import ilib.client.GeneratedModelRepo;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ModelLoader;

import java.util.Collection;

public class TypedModelHelper {

    public static void itemTypedModel(Item item, ResourceLocation model, String texturePath, IRegistry<?> wrapper) {
        itemTypedModel(item, model, texturePath, wrapper.values());
    }

    public static void itemTypedModel(Item item, ResourceLocation model, String texturePath, Indexable[] values) {
        itemTypedModel(item, model, texturePath, values, true);
    }

    public static void itemTypedModel(Item item, ResourceLocation model, String textureDirectory, Indexable[] values, boolean register) {
        BlockStateBuilder modelBuilder = new BlockStateBuilder(true).addVariant("type");

        String base = model.getNamespace() + ":items/" + textureDirectory + '/';

        for (Indexable t : values) {
            modelBuilder.setVariantTexture("type", t.getName(), base + t.getName());
            if (register) {
                ModelLoader.setCustomModelResourceLocation(item, t.getIndex(), new ModelResourceLocation(model, "type" + '=' + t.getName()));
            }
        }

        GeneratedModelRepo.addModel("assets/" + model.getNamespace() + "/blockstates/" + model.getPath() + ".json", modelBuilder.build());
    }

    public static void typeModelMerged(ResourceLocation model, String texturePath, Collection<String> values) {
        typeModelMerged(new BlockStateBuilder(true), "layer0", model, texturePath, values);
    }

    public static void typeModelMerged(BlockStateBuilder b, String textureType, ResourceLocation model, String textureDirectory, Collection<String> values) {
        b.addVariant("type");

        String base = model.getNamespace() + ":items/" + textureDirectory + '/';

        for (String t : values) {
            b.setVariantTexture("type", t, textureType, base + t);
        }

        GeneratedModelRepo.addModel("assets/" + model.getNamespace() + "/blockstates/" + model.getPath() + ".json", b.build());
    }
}
