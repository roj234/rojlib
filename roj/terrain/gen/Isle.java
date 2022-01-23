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
package roj.terrain.gen;

import roj.collect.IntMap;
import roj.math.Vec2d;
import roj.terrain.Center;
import roj.terrain.Corner;
import roj.terrain.Edge;
import roj.terrain.RectX;
import roj.terrain.hmap.HeightMap;
import roj.terrain.tmap.PolyMap;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * 标准地形生成器
 * @author Roj233
 * @since 2021/9/11 23:18
 */
public class Isle<T> {
    private class CornerGenerator implements Function<Vec2d, Corner>, IntFunction<Corner> {
        final IntMap<Corner> dedupCorners = new IntMap<>();
        Vec2d p;

        @Override
        //ensures that each corner is represented by only one corner object
        public Corner apply(Vec2d p) {
            if (p == null) return null;

            int index = (int) ((int) p.x + (int) (p.y) * bounds.width * 2);
            this.p = p;

            return dedupCorners.computeIfAbsent(index, this);
        }

        @Override
        public Corner apply(int value) {
            Corner c1 = new Corner();
            c1.loc = p;
            c1.border = bounds.liesOnAxes(p);
            c1.index = corners.size();
            corners.add(c1);
            return c1;
        }
    }

    protected final Random       r;
    protected final List<Edge>   edges;
    protected final List<Corner> corners;
    protected final List<Center> centers;
    protected final RectX        bounds;

    public Isle(PolyMap v, Random r) {
        this.r = r;
        this.edges = new ArrayList<>(v.polygonCount());
        this.corners = new ArrayList<>(v.polygonCount() * 3);
        this.centers = new ArrayList<>(v.polygonCount());
        this.bounds = v.getBounds();
    }

    protected final void generate(PolyMap v, HeightMap algo) {
        // region Step 1: init
        init(v);
        // endregion
        Deque<Object> tmp = new ArrayDeque<>();
        // region Step 2: height map
        heightMap(algo, tmp);
        // endregion
        List<Corner> landCorners = new ArrayList<>();
        // region Step 3: Classify ocean, coast and land
        classify(tmp, landCorners);
        // endregion
        // region Step 4: apply height
        height(landCorners);
        // endregion
        // region Step 5: river
        makeRiver();
        // endregion
        // region Step 6: moisture
        moisture(tmp, landCorners);
        // endregion
    }

    private void moisture(Deque<Object> tmp, List<Corner> landCorners) {
        // compute
        for (int i = 0; i < corners.size(); i++) {
            Corner c = corners.get(i);
            if ((c.water || c.river > 0) && !c.ocean) {
                c.moisture = c.river > 0 ? Math.min(3.0, (0.2 * c.river)) : 1.0;
                tmp.push(c);
            } else {
                c.moisture = 0;
            }
        }

        while (!tmp.isEmpty()) {
            Corner c = (Corner) tmp.pop();

            for (Corner a : c._neighbors2) {
                double newM = .9 * c.moisture;

                if (newM > a.moisture) {
                    a.moisture = newM;
                    tmp.add(a);
                }
            }
        }

        // normalize
        landCorners.sort((d0, d1) -> Double.compare(d0.moisture, d1.moisture));

        for (int i = 0; i < landCorners.size(); i++)
            landCorners.get(i).moisture = (double) i / landCorners.size();

        // ocean
        for (int i = 0; i < corners.size(); i++) {
            Corner c = corners.get(i);
            if (c.ocean || c.coast) c.moisture = 1.0;
        }

        // apply
        for (int i = 0; i < centers.size(); i++) {
            Center center = centers.get(i);
            double total = 0;

            List<Corner> corners = center._collided1;
            for (int j = 0; j < corners.size(); j++) {
                total += corners.get(j).moisture;
            }

            center.moisture = total / center._collided1.size();
        }
    }

    private void makeRiver() {
        // down slopes
        for (int i = 0; i < corners.size(); i++) {
            Corner c = corners.get(i);

            Corner down = c;
            //System.out.println("ME: " + c.elevation);
            List<Corner> adj = c._neighbors2;
            for (int j = 0; j < adj.size(); j++) {
                Corner a = adj.get(j);
                //System.out.println(a.elevation);
                if (a.height <= down.height) down = a;
            }

            c.downslope = down;
        }

        ArrayUtil.shuffle(corners, r);
        int amount = corners.size() / 20;
        for (int i = 0; i < amount; i++) {
            Corner c = corners.get(r.nextInt(corners.size()));

            if (c.ocean || c.height < 0.3 || c.height > 0.9) {
                continue;
            }

            // Bias rivers to go west
            //   if (q.downslope.x > q.x) continue;
            while (!c.coast) {
                if (c == c.downslope) break;

                List<Edge> protrudes = c._edges2;
                Edge edge = Helpers.nonnull();
                for (int j = 0; j < protrudes.size(); j++) {
                    edge = protrudes.get(j);
                    if (edge.v0 == c.downslope || edge.v1 == c.downslope)
                        break;
                }

                // todo test, cancel river flow
                //if (r.nextFloat() > 0.9) break;

                if (!edge.v0.water || !edge.v1.water) {
                    edge.river++;
                    c.river++;
                    c.downslope.river++;  // TODO: fix double count
                }

                c = c.downslope;
            }
        }
    }

    private void height(List<Corner> landCorners) {
        landCorners.sort((d0, d1) -> Double.compare(d0.height, d1.height));

        final double SCALE_FACTOR = 1.1;
        double SQRT_SCALE_FACTOR = Math.sqrt(SCALE_FACTOR);

        // normalize
        for (int i = 0; i < landCorners.size(); i++) {
            double y = (double) i / landCorners.size();
            double x = SQRT_SCALE_FACTOR - Math.sqrt(SCALE_FACTOR * (1 - y));
            landCorners.get(i).height = Math.min(x, 1);
        }

        // ocean
        for (int i = 0; i < corners.size(); i++) {
            Corner c = corners.get(i);
            if (c.ocean || c.coast) c.height = 0;
        }

        // apply
        for (int i = 0; i < centers.size(); i++) {
            Center center = centers.get(i);
            double total = 0;

            List<Corner> corners = center._collided1;
            for (int j = 0; j < corners.size(); j++) {
                Corner c = corners.get(j);
                total += c.height;
            }

            center.height = total / center._collided1.size();
        }
    }

    private void classify(Deque<Object> tmp, List<Corner> landCorners) {
        final double waterThreshold = .3;

        for (int i = 0; i < centers.size(); i++) {
            Center center = centers.get(i);
            int numWater = 0;

            List<Corner> corners = center._collided1;
            for (int j = 0; j < corners.size(); j++) {
                Corner c = corners.get(j);
                if (c.border) {
                    center.border = center.water = center.ocean = true;
                    tmp.add(center);
                }

                if (c.water) {
                    numWater++;
                }
            }

            center.water = center.ocean || ((double) numWater / center._collided1.size() >= waterThreshold);
        }

        // Fill all water direct linked to border with ocean
        while (!tmp.isEmpty()) {
            Center center = (Center) tmp.pop();

            List<Center> neighbors = center._neighbors1;
            for (int i = 0; i < neighbors.size(); i++) {
                Center n = neighbors.get(i);
                if (n.water && !n.ocean) {
                    n.ocean = true;
                    tmp.add(n);
                }
            }
        }

        for (int i = 0; i < centers.size(); i++) {
            Center center = centers.get(i);
            boolean oceanNeighbor = false;
            boolean landNeighbor = false;

            List<Center> neighbors = center._neighbors1;
            for (int j = 0; j < neighbors.size(); j++) {
                Center n = neighbors.get(j);
                oceanNeighbor |= n.ocean;
                landNeighbor |= !n.water;
                if (oceanNeighbor & landNeighbor) {
                    center.coast = true;
                    break;
                }
            }
        }

        for (int i = 0; i < corners.size(); i++) {
            Corner c = corners.get(i);
            int numOcean = 0;
            int numLand = 0;

            List<Center> touches = c._collided2;
            for (int j = 0; j < touches.size(); j++) {
                Center center = touches.get(j);
                numOcean += center.ocean ? 1 : 0;
                numLand += !center.water ? 1 : 0;
            }

            c.ocean = numOcean == touches.size();
            c.coast = numOcean > 0 && numLand > 0;
            c.water = c.border || ((numLand != touches.size()) && !c.coast);

            if (numOcean < touches.size() && (numOcean == 0 || numLand == 0))
                landCorners.add(c);
        }
    }

    private void heightMap(HeightMap algo, Deque<Object> tmp) {
        for (int i = 0; i < corners.size(); i++) {
            Corner c = corners.get(i);
            if (!c.border) {
                double x = 0;
                double y = 0;

                List<Center> touches = c._collided2;
                for (int j = 0; j < touches.size(); j++) {
                    Center center = touches.get(j);
                    x += center.loc.x;
                    y += center.loc.y;
                }

                // move to center (optional)
                c.loc.set(x / touches.size(), y / touches.size());
                c.height = Double.MAX_VALUE;
                //c.height = algo.getExceptedHeight(c.loc, bounds, r);
            } else {
                tmp.add(c);
                // is default value
                //c.height = 0;
                //c.height = algo.getExceptedHeight(c.loc, bounds, r);
            }
            c.water = algo.isWater(c.loc, bounds, r);
        }

        // ocean to land elevation
        while (!tmp.isEmpty()) {
            Corner c = (Corner) tmp.pop();

            List<Corner> adj = c._neighbors2;
            for (int i = 0; i < adj.size(); i++) {
                Corner a = adj.get(i);
                double newElevation = 0.01 + c.height;

                if (!c.water && !a.water) {
                    newElevation += 1;
                }

                if (newElevation < a.height) {
                    a.height = newElevation;
                    tmp.add(a);
                }
            }
        }
        System.out.println(corners);
    }

    private void init(PolyMap v) {
        v.genPolygons(centers, corners, edges, new CornerGenerator());
        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            // Centers point to edges. Corners point to edges.
            if (edge.d0 != null) {
                edge.d0._edges1.add(edge);

                // center => corner
                add1(edge.d0._collided1, edge.v0);
                add1(edge.d0._collided1, edge.v1);
            }
            if (edge.d1 != null) {
                edge.d1._edges1.add(edge);

                // center => corner
                add1(edge.d1._collided1, edge.v0);
                add1(edge.d1._collided1, edge.v1);

                // d0 & d1
                if (edge.d0 != null) {
                    // center => center
                    add0(edge.d0._neighbors1, edge.d1);
                    add0(edge.d1._neighbors1, edge.d0);
                }
            }
            if (edge.v0 != null) {
                edge.v0._edges2.add(edge);

                // corner => center
                add0(edge.v0._collided2, edge.d0);
                add0(edge.v0._collided2, edge.d1);
            }
            if (edge.v1 != null) {
                edge.v1._edges2.add(edge);

                // corner => center
                add0(edge.v1._collided2, edge.d0);
                add0(edge.v1._collided2, edge.d1);

                // v0 & v1
                if (edge.v0 != null) {
                    // corner => corner
                    add1(edge.v0._neighbors2, edge.v1);
                    add1(edge.v1._neighbors2, edge.v0);
                }
            }
        }
    }

    // inline
    private static void add1(List<Corner> list, Corner c) {
        if (c != null) {
            if (!list.contains(c))
                list.add(c);
        }
    }

    // inline
    private static void add0(List<Center> list, Center c) {
        if (c != null) {
            if (!list.contains(c))
                list.add(c);
        }
    }
}
