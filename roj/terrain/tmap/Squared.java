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
package roj.terrain.tmap;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.math.Vec2d;
import roj.terrain.Center;
import roj.terrain.Corner;
import roj.terrain.Edge;
import roj.terrain.RectX;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/9/12 13:19
 */
public class Squared implements PolyMap {
    private final RectX  bounds;
    private final Random r;
    private final int    count;
    private final double randomShift;

    final MyHashSet<Vec2d> PS = new MyHashSet<>();
    final Vec2d            P  = new Vec2d();
    final Vec2d P(double x, double y) {
        Vec2d P = this.P;
        if ((P = PS.find(P.set(x, y))) == this.P) {
            PS.add(P = new Vec2d(P));
        }
        return P;
    }

    /** <pre>
     *         a          b          c
     *    0    +----------|----------+
     *         | A        | .        |
     *         |          |          |
     *         |       B  |        . |
     *    1    +----------|----------+
     *         | .        | .        |
     *         |          |          |
     *         |        . |        . |
     *    2    +----------|----------+
     */
    @Override
    public void genPolygons(List<Center> centers, List<Corner> corners, List<Edge> edges, Function<Vec2d, Corner> corner) {
        PS.clear();

        double dx = Math.sqrt((bounds.width * bounds.height) / count);
        final double dxzc = dx / 3;

        double x = 0, y = 0;

        Map<Vec2d, Center> CENTERS = new MyHashMap<>();
        Function<Vec2d, Center> center = p -> {
            Center c = CENTERS.get(p);
            if (c == null) {
                c = new Center(centers.size(), p);
                CENTERS.put(p, c);
                centers.add(c);
            }
            return c;
        };

        while (y < bounds.height) {
            while (x < bounds.width) {
                Vec2d TL = P(x, y);

                // 边a0-a1, 经过三角形 A - A左边的
                Edge edge = new Edge();
                Corner A = edge.v0 = corner.apply(P(x + dxzc, y + dxzc));
                edge.v1 = corner.apply(x < dxzc ? P(x + dx / 2, y) : P(x - dxzc, y + dxzc * 2));
                Center cTL = edge.d0 = center.apply(TL);
                Center cY = edge.d1 = center.apply(P(x, y + dx));
                edge.index = edges.size();
                edges.add(edge);

                // 边a1-b0, 经过三角形 A - B
                edge = new Edge();
                edge.v0 = A;
                edge.v1 = corner.apply(P(x + dxzc * 2, y + dxzc * 2));
                edge.d0 = cY;
                Center cX = edge.d1 = center.apply(P(x + dx, y));
                edge.index = edges.size();
                edges.add(edge);

                // 边a0-b0, 经过三角形 A - A上边的
                edge = new Edge();
                edge.v0 = corner.apply(y < dxzc ? P(x, y + dx / 2) : P(x + dxzc * 2, y - dxzc));
                edge.v1 = A;
                edge.d0 = cTL;
                edge.d1 = cX;
                edge.index = edges.size();
                edges.add(edge);

                x += dx;
            }
            x = 0;
            y += dx;
        }

        /*y -= dx;
        while (x < bounds.width) {
            // 边a2-b2, 经过三角形 U - null
            Edge edge = new Edge();
            edge.v0 = corner.apply(new Vec2d(x + 2 * dxzc, y - dxzc)); // must new
            edge.v1 = corner.apply(new Vec2d(x + dx / 2, y)); // must new
            edge.d0 = CENTERS.get(P.set(x, y)); // must old
            if (CENTERS.get(P.set(x + dx, y)) == null) System.out.println("null " + x + "," + y);
            edge.d1 = center.apply(P(x + dx, y)); // nearly must old
            edge.index = edges.size();
            edges.add(edge);

            x += dx;
        }

        x -= dx;
        y = 0;
        while (y < bounds.height) {
            // 边c0-c1, 经过三角形 null - V
            Edge edge = new Edge();
            edge.v0 = corner.apply(new Vec2d(x, y + dx / 2)); // new
            edge.v1 = corner.apply(new Vec2d(x - dxzc, y + 2 * dxzc)); // old
            edge.d0 = CENTERS.get(P.set(x, y));
            edge.d1 = CENTERS.get(P.set(x, y + dx));
            edge.index = edges.size();
            edges.add(edge);

            y += dx;
        }*/

        dx *= randomShift;
        for (Vec2d p : PS) {
            if (p.y <= 0 || p.y >= bounds.height ||
                p.x <= 0 || p.x >= bounds.width) {
                continue;
            }

            p.x += (r.nextDouble() - 0.5) * dx;
            p.y += (r.nextDouble() - 0.5) * dx;

            if (p.y < 0) {
                p.y = 0;
            } else
            if (p.y > bounds.height) {
                p.y = bounds.height;
            }
            if (p.x < 0) {
                p.x = 0;
            } else
            if (p.x > bounds.width) {
                p.x = bounds.width;
            }
        }
        PS.clear();
    }

    public RectX getBounds() {
        return bounds;
    }

    @Override
    public int polygonCount() {
        return count;
    }

    /**
     * @param count Amount of sites.
     * @param width Graph width.
     * @param height Graph height.
     * @param r Randomizer.
     */
    public Squared(int count, double width, double height, Random r) {
        this.bounds = new RectX(0, 0, width, height);
        this.count = count;
        this.r = r;
        this.randomShift = 0.66;
    }
}
