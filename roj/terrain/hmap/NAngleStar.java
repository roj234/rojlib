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
package roj.terrain.hmap;

import roj.math.Vec2d;
import roj.terrain.RectX;

import java.util.Random;

/**
 * N角星
 * @author Roj233
 * @since 2021/9/11 23:18
 */
public class NAngleStar implements HeightMap {
    public NAngleStar(int angles) {
        this.angles = angles;
    }

    final int angles;

    @Override
    public boolean isWater(Vec2d point, RectX bounds, Random r) {
        Vec2d p = new Vec2d(2 * (point.x / bounds.width - 0.5), 2 * (point.y / bounds.height - 0.5));

        boolean centerLake = new Vec2d(p.x, p.y).len() < 0.33;
        boolean holder = p.y < -0.2 && Math.abs(p.x) < 0.1;
        boolean body = p.len() < 0.8 - 0.18 * Math.sin(angles * Math.atan2(p.y, p.x));

        return (!body || (centerLake && r.nextFloat() > 0.4)) && !holder;
    }

    @Override
    public float getExceptedHeight(Vec2d p, RectX bounds, Random random) {
        return isWater(p, bounds, random) ? 1 : 0;
    }

    @Override
    public float getSeaLevel() {
        return 0.5f;
    }
}