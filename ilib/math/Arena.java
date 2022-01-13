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

package ilib.math;

import net.minecraft.util.math.BlockPos;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/2 15:22
 */
public class Arena {
    protected BlockPos p1 = null;
    protected BlockPos p2 = null;

    public Arena() {
    }

    public Section toSection() {
        if (isOK())
            return new Section(p1, p2);
        return null;
    }

    public int getSelectionSize() {
        if (isOK())
            return toSection().volume();
        return -1;
    }

    public boolean isOK() {
        return p1 != null && p2 != null;
    }

    public BlockPos getP1() {
        return p1;
    }

    public void setPos1(BlockPos p1) {
        this.p1 = p1;
    }

    public BlockPos getP2() {
        return p2;
    }

    public void setPos2(BlockPos p2) {
        this.p2 = p2;
    }
}