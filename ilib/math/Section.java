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

import roj.math.Rect3i;

import net.minecraft.util.math.BlockPos;

/**
 * @author Roj234
 * @since 2020/9/20 1:15
 */
public class Section extends Rect3i implements PositionProvider {
    public Section() {}

    public Section(int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
        super(xmin, ymin, zmin, xmax, ymax, zmax);
    }

    public Section(BlockPos min, BlockPos max) {
        super(min.getX(),
                min.getY(),
                min.getZ(),
                max.getX(),
                max.getY(),
                max.getZ());
    }

    public Section(Rect3i other) {
        super(other);
    }

    /**
     * Sets this rectangle according to the specified parameters.
     *
     * @param min the minimum axis values contained in this rectangle.
     * @param max the maximum axis values contained in this rectangle.
     * @return this rectangle.
     */
    public Section set(BlockPos min, BlockPos max) {
        this.xmin = min.getX();
        this.ymin = min.getY();
        this.zmin = min.getZ();

        this.xmax = max.getX();
        this.ymax = max.getY();
        this.zmax = max.getZ();

        return this;
    }

    /**
     * Returns the minimum axis values of this rectangle.
     *
     * @return the minimum axis values.
     */
    public BlockPos minBlock() {
        return new BlockPos(xmin, ymin, zmin);
    }

    /**
     * Returns the maximum axis values of this rectangle.
     *
     * @return the maximum axis values.
     */
    public BlockPos maxBlock() {
        return new BlockPos(xmax, ymax, zmax);
    }

    public BlockPos[] verticesBlock() {
        return new BlockPos[]{
                new BlockPos(xmin, ymin, zmin), // 000
                new BlockPos(xmin, ymin, zmax), // 001
                new BlockPos(xmin, ymax, zmin), // 010
                new BlockPos(xmin, ymax, zmax), // 011
                new BlockPos(xmax, ymin, zmin), // 100
                new BlockPos(xmax, ymin, zmax), // 101
                new BlockPos(xmax, ymax, zmin), // 110
                new BlockPos(xmax, ymax, zmax)  // 111
        };
    }

    public BlockPos[] minMaxBlock() {
        return new BlockPos[]{
                new BlockPos(xmin, ymin, zmin), // 000
                new BlockPos(xmax, ymax, zmax) // 111
        };
    }

    @Override
    public Section getSection() {
        return this;
    }

    @Override
    public int getWorld() {
        return 0;
    }

    public boolean contains(BlockPos p) {
        return xmin <= p.getX() && p.getX() <= xmax && ymin <= p.getY() && p.getY() <= ymax && zmin <= p.getZ() && p.getZ() <= zmax;
    }

    public void expandIf(BlockPos pos) {
        this.expandIf(pos.getX(), pos.getY(), pos.getZ());
    }

    public Section copy() {
        return new Section(this);
    }

    public void merge(Section other) {
        this.xmin = Math.min(other.xmin, this.xmin);
        this.ymin = Math.min(other.ymin, this.ymin);
        this.zmin = Math.min(other.zmin, this.zmin);
        this.xmax = Math.max(other.xmax, this.xmax);
        this.ymax = Math.max(other.ymax, this.ymax);
        this.zmax = Math.max(other.zmax, this.zmax);
    }

    public int yLen() {
        return ymax - ymin;
    }

    public int zLen() {
        return zmax - zmin;
    }

    public int xLen() {
        return xmax - xmin;
    }

    public Section offset(BlockPos offset) {
        this.xmin += offset.getX();
        this.ymin += offset.getY();
        this.zmin += offset.getZ();
        this.xmax += offset.getX();
        this.ymax += offset.getY();
        this.zmax += offset.getZ();

        return this;
    }
}