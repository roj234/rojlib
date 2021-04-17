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
package ilib.misc;

import ilib.math.AStar3D;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/2 18:44
 */
public class PathFinderBlock extends AStar3D {
    public World world;

    public final BlockPos.MutableBlockPos sharedMCPos = new BlockPos.MutableBlockPos();

    public PathFinderBlock() {}

    protected double distance(Point a, Point b) {
        double dist = a.sub(b).len();
        a.add(b);
        return dist;
    }

    public boolean canWalk(int i) {
        return i < 32 && open.size() < 192 && closed.size() < 192;
    }

    public boolean valid(int x, int y, int z) {
        return world.isChunkGeneratedAt(x >> 4, z >> 4) && world.isAirBlock(sharedMCPos.setPos(x, y, z));
    }
}
