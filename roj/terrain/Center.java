/*
 * This file is a part of MoreItems
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
package roj.terrain;

import roj.math.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 三角形的点，同时也是block的中心
 * //todo 支持任意多边形
 * @author Roj233
 * @since 2021/9/11 23:18
 */
public class Center {
    public int index;
    public Vec2d loc;

    public List<Corner> _collided1  = new ArrayList<>();
    public List<Center> _neighbors1 = new ArrayList<>();
    public List<Edge>   _edges1     = new ArrayList<>();

    public boolean border, ocean, water, coast;
    public double height, moisture;

    /** 面积, WIP */
    public double area;

    public Object biome;

    public Center(int index, Vec2d loc) {
        this.index = index;
        this.loc = loc;
    }
}
