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
package ilib.client.util.model;

import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.Type;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ItemModelBuilder {
    public final CMapping jsonData;

    public ItemModelBuilder() {
        jsonData = new CMapping();
        jsonData.put("parent", "builtin/generated");
    }

    public String build() {
        return jsonData.toJSON();
    }

    public ItemModelBuilder setModel(String model) {
        jsonData.put("model", model);
        return this;
    }

    public ItemModelBuilder parent(String parent) {
        jsonData.put("parent", parent);
        return this;
    }

    public ItemModelBuilder setTexture(String texture) {
        return setTexture("all", texture);
    }

    public ItemModelBuilder setTexture(String textureName, String texture) {
        if (!jsonData.containsKey("textures")) {
            jsonData.put("textures", new CMapping());
        }
        jsonData.get("textures").asMap().put(textureName, texture);
        return this;
    }

    private CMapping newOverride() {
        if (!jsonData.containsKey("overrides", Type.LIST)) {
            jsonData.put("overrides", new CList());
        }
        CList list = jsonData.get("overrides").asList();
        CMapping mapping = new CMapping();
        list.add(mapping);
        return mapping;
    }

    public ItemModelBuilder setOverrides(CMapping predicate, String model) {
        CMapping map = newOverride();
        map.put("predicate", predicate);
        map.put("model", model);
        return this;
    }

    public ItemModelBuilder setOverrides(String predicate, float value, String model) {
        CMapping map = new CMapping();
        map.put(predicate, value);
        return setOverrides(map, model);
    }
}
