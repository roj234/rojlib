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
package ilib.world.structure.cascading;

import ilib.world.structure.cascading.api.IGenerationData;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/19 23:46
 */
public class GenerationData implements IGenerationData {
    final EnumFacing direction;
    final BlockPos pos;
    final String group;

    public GenerationData(EnumFacing direction, BlockPos pos, String group) {
        this.direction = direction;
        this.pos = pos;
        this.group = group;
    }

    @Override
    public EnumFacing getDirection() {
        return direction;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public String getNextGroup() {
        return group;
    }
}
