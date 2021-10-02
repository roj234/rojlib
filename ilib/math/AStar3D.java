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

import roj.collect.BSLowHeap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.math.Vec3i;
import roj.util.ArrayUtil;

import java.util.Comparator;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/2 18:44
 */
public abstract class AStar3D {
    private final int cacheSize;
    private final Point[] cache;
    private int cacheIdx = -1;

    protected final BSLowHeap<Point> open = new BSLowHeap<>(PointCpr.INSTANCE),
            tmp                           = new BSLowHeap<>(PointCpr.INSTANCE);
    public List<Point> result;

    protected final MyHashSet<Point> closed = new MyHashSet<>();

    protected final Point retainP(int x, int y, int z) {
        if (cacheIdx >= 0)
            return (Point) cache[cacheIdx].set(x, y, z);
        return new Point(null, x, y, z);
    }

    protected final void releaseP(Point pos) {
        if (cacheIdx < cacheSize)
            cache[++cacheIdx] = pos;
    }

    public AStar3D() {
        this(256);
        // todo test
    }

    public AStar3D(int cache) {
        cacheSize = cache - 1;
        this.cache = new Point[cache];
    }

    protected int costYU = 8, costYD = 8,
            costXU = 6, costXD = 6,
            costZU = 6, costZD = 6;

    public final List<Point> find(int strX, int strY, int strZ, int endX, int endY, int endZ) {
        result = null;

        BSLowHeap<Point> open = this.open;
        open.clear();
        MyHashSet<Point> closed = this.closed;
        closed.clear();
        BSLowHeap<Point> tmp = this.tmp;
        tmp.clear();

        long startTime = System.currentTimeMillis();

        Point str = retainP(strX, strY, strZ);
        open.add(str);

        Point end = retainP(endX, endY, endZ);

        int i = 0;
        while (canWalk(i++)) {
            if(open.get(0).equals(end)) {
                for (Point pos : closed) {
                    releaseP(pos);
                }
                for (int j = 0; j < open.size(); j++) {
                    releaseP(open.get(j));
                }
                return result = resolvePath(open.get(0));
            }

            if (open.isEmpty()) {
                break;
            }

            for (int j = 0; j < open.size(); j++) {
                Point pos = open.get(j);
                int x = pos.x;
                int y = pos.y;
                int z = pos.z;
                check(pos, tmp, costYD, x, y - 1, z, end);
                check(pos, tmp, costYU, x, y + 1, z, end);
                check(pos, tmp, costZD, x, y, z - 1, end);
                check(pos, tmp, costZU, x, y, z + 1, end);
                check(pos, tmp, costXD, x - 1, y, z, end);
                check(pos, tmp, costXU, x + 1, y, z, end);
            }

            open.clear();

            BSLowHeap<Point> tmp1 = open;
            open = tmp;
            tmp = tmp1;
        }

        for (Point pos : closed) {
            releaseP(pos);
        }

        return null;
    }

    protected final void check(Point parent, BSLowHeap<Point> list, int cost, int x, int y, int z, Point end) {
        if (valid(x, y, z)) {
            final Point node = retainP(x, y, z);

            node.parent = parent;
            node.cost = parent.cost + cost;

            //如果访问到开放列表中的点，则将此点重置为消耗最小的路径,否则添加到开放列表
            int i = list.indexOf(node);
            if(i != -1) {
                Point in = list.get(i);
                if(node.cost < in.cost) {
                    in.parent = parent;
                    in.cost = node.cost;
                    in.distant = node.distant;
                }
                releaseP(node);
            } else {
                // 已在关闭列表则不做处理
                if(!closed.add(node)) {
                    releaseP(node);
                    return;
                }

                node.distant = distance(node, end);

                list.add(node);
            }
        }
    }

    protected abstract double distance(Point a, Point b);

    public abstract boolean canWalk(int i);

    public abstract boolean valid(int x, int y, int z);

    protected static List<Point> resolvePath(Point node) {
        SimpleList<Point> path = new SimpleList<>();
        do {
            path.add(node);
        } while ((node = node.parent) != null);
        ArrayUtil.inverse(path.getRawArray(), path.size());

        return path;
    }

    public static class PointCpr implements Comparator<Point> {
        public static final PointCpr INSTANCE = new PointCpr();

        @Override
        public int compare(Point o1, Point o2) {
            return Double.compare(o1.cost + o1.distant, o2.cost + o2.distant);
        }
    }

    public static class Point extends Vec3i {
        Point parent;
        int cost;
        double distant;

        public Point(Point parent, int x, int y, int z) {
            super(x, y, z);
            this.parent = parent;
        }
    }
}
