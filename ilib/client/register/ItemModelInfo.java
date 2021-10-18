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
package ilib.client.register;

import ilib.ImpLib;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ItemModelInfo extends ModelInfo {
    public final Item item;
    public final int meta;
    public final ModelResourceLocation model;

    public ItemModelInfo(Item item, boolean merged) {
        this(item, 0, merged, false);
    }

    public ItemModelInfo(Item item, int meta, boolean merged, boolean block) {
        this.item = item;
        this.meta = meta;
        if (merged) {
            this.model = new ModelResourceLocation(ImpLib.MODID + ":generated/" + (block ? "blocks" : "items"), "type=" + item.getRegistryName().getPath());
        } else {
            this.model = new ModelResourceLocation(item.getRegistryName(), "inventory");
        }
    }

    public ItemModelInfo(Item item, ModelResourceLocation loc) {
        this(item, 0, loc);
    }

    public ItemModelInfo(Item item, int meta, ModelResourceLocation loc) {
        this.item = item;
        this.meta = meta;
        this.model = loc;
    }

    public ItemModelInfo(Item item, int meta, String loc) {
        this.item = item;
        this.meta = meta;
        this.model = new ModelResourceLocation(ImpLib.MODID + ":generated/items", "type=" + loc);
    }
}
