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
package ilib.misc.model;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.data.Type;

/**
 * @author Roj234
 * @since  2020/6/13 11:04
 */
public class BlockStateBuilder {
    public final CMapping jsonData, defaults, variants;

    public final boolean isItem;

    public BlockStateBuilder(String jsonData1, boolean isItemModel) {
        try {
            jsonData = JSONParser.parse(jsonData1).asMap();
        } catch (ParseException e) {
            throw new RuntimeException("Illegal model data ", e);
        }
        defaults = jsonData.getOrCreateMap("defaults");
        variants = jsonData.getOrCreateMap("variants");
        isItem = isItemModel;
    }

    public BlockStateBuilder(boolean isItemModel) {
        try {
            jsonData = JSONParser.parse(isItemModel ? "{\n" +
                    "  \"forge_marker\": 1,\n" +
                    "  \"defaults\": {\n" +
                    "    \"model\": \"builtin/generated\",\n" +
                    "    \"transform\": \"forge:default-item\"\n" +
                    "  },\n" +
                    "  \"variants\": {\n" +
                    "    \"inventory\": [{}]\n" +
                    "  }\n" +
                    "}" : "{\n" +
                    "  \"forge_marker\": 1,\n" +
                    "  \"defaults\": {},\n" +
                    "  \"variants\": {\n" +
                    "    \"inventory\": [{}]\n" +
                    "  }\n" +
                    "}").asMap();
        } catch (ParseException e) {
            throw new RuntimeException("It can't happen! ", e);
        }
        defaults = jsonData.get("defaults").asMap();
        variants = jsonData.get("variants").asMap();
        isItem = isItemModel;
    }

    public CMapping getDefault() {
        return defaults;
    }

    public String build() {
        return jsonData.toShortJSON();
    }

    public BlockStateBuilder addVariant4D() {
        return addVariant4D("facing");
    }

    public BlockStateBuilder addVariant4D(String k) {
        CMapping map1 = new CMapping();
        map1.put("y", 0);
        CMapping map2 = new CMapping();
        map2.put("y", 180);
        CMapping map3 = new CMapping();
        map3.put("y", 270);
        CMapping map4 = new CMapping();
        map4.put("y", 90);
        return addVariant(k)
                .setVariantNonType(k, "north", map1)
                .setVariantNonType(k, "south", map2)
                .setVariantNonType(k, "west", map3)
                .setVariantNonType(k, "east", map4);
    }

    public BlockStateBuilder merge(BlockStateBuilder another) {
        this.variants.merge(another.variants, false, true);
        return this;
    }

    public BlockStateBuilder addVariant(String key) {
        variants.getOrCreateMap(key);
        return this;
    }

    public BlockStateBuilder addVariantValue(String key, String value) {
        variants.getOrCreateMap(key).getOrCreateMap(value);
        return this;
    }

    public BlockStateBuilder setVariantNonType(String key, String tag, CEntry entry) {
        variants.getOrCreateMap(key).put(tag, entry);
        return this;
    }

    public BlockStateBuilder setVariantNonTypeModel(String key, String model) {
        variants.getOrCreateMap(key).put("model", model);
        return this;
    }

    public BlockStateBuilder addDefaultEntry(String name, String json) {
        try {
            defaults.put(name, JSONParser.parse(json));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return this;
    }

    public BlockStateBuilder setDefaultModel(String model) {
        if (model == null) {
            defaults.remove("model");
        } else {
            defaults.put("model", model);
        }
        return this;
    }

    /*public BlockStateBuilder parent(String parent) {
        if(parent == null) {
            defaults.remove("parent");
        } else {
            defaults.put("parent", parent);
        }
        return this;
    }*/

    public BlockStateBuilder setDefaultTexture(String texture) {
        return setDefaultTexture("all", texture);
    }

    public BlockStateBuilder setDefaultTexture(String textureName, String texture) {
        CMapping map = defaults.getOrCreateMap("textures");

        if (texture == null) {
            defaults.remove(textureName);
        } else {
            defaults.put(textureName, texture);
        }

        return this;
    }

    public CMapping getVariantMap(String key, String value) {
        return variants.getOrCreateMap(key).getOrCreateMap(value);
    }

    public BlockStateBuilder setVariantModel(String key, String value, String model) {
        getVariantMap(key, value).put("model", model);
        return this;
    }

    public BlockStateBuilder setVariantTexture(String key, String value, String texture) {
        return setVariantTexture(key, value, isItem ? "layer0" : "all", texture);
    }

    public BlockStateBuilder setVariantTexture(String key, String value, String textureName, String texture) {
        CMapping variant = getVariantMap(key, value);
        if (!variant.containsKey("textures")) {
            variant.put("textures", new CMapping());
        }
        variant.get("textures").asMap().put(textureName, texture);
        return this;
    }

    public BlockStateBuilder uvLock() {
        defaults.put("uvlock", true);
        return this;
    }

    public boolean hasVariant(String key, String value) {
        return variants.containsKey(key, Type.MAP) && variants.get(key).asMap().containsKey(value, Type.MAP);
    }
}
