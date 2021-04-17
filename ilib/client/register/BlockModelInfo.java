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
import ilib.util.Registries;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;

import javax.annotation.Nullable;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class BlockModelInfo extends ModelInfo {
    public final Block block;
    public final ModelResourceLocation model;

    public BlockModelInfo(Block block, boolean merged) {
        this.block = block;
        if (merged) {
            this.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + block.getRegistryName().getPath());
        } else {
            model = null;
        }
    }

    public BlockModelInfo(Block block, ModelResourceLocation loc) {
        this.block = block;
        this.model = loc;
    }

    public BlockModelInfo(Block block, String loc) {
        this.block = block;
        this.model = new ModelResourceLocation(ImpLib.MODID + ":generated/blocks", "type=" + loc);
    }

    @Nullable
    public Item item() {
        return Registries.b2i().get(block);
    }
}
