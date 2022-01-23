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

import roj.collect.IntMap;
import roj.collect.LongMap;
import roj.collect.MyHashSet;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class FastPath<V extends PositionProvider>/* implements Iterable<V>*/ {
    private final IntMap<LongMap<Collection<V>>> fastPath = new IntMap<>(2);

    private final int shift;

    public FastPath() {
        this(4);
    }

    public FastPath(int shift) {
        this.shift = shift;
    }

    public V get(int world, BlockPos pos) {
        LongMap<Collection<V>> map = fastPath.get(world);
        if (map != null) {
            long chunkPos = ((long) (pos.getX() >>> shift) << 42) | ((long) pos.getY() >>> shift) << 21 | pos.getZ() >>> shift;
            Collection<V> list = map.get(chunkPos);
            if (list != null) {
                for (V data : list) {
                    if (data.getSection().contains(pos) && data.contains(pos))
                        return data;
                }
            }
        }
        return null;
    }

    public boolean collidesWith(int world, Section section) {
        LongMap<Collection<V>> map = fastPath.get(world);
        if (map != null) {
            for (int x = section.xmin, xm = section.xmax; x < xm; x += 1 << shift) {
                for (int y = section.ymin, ym = section.ymax; y < ym; y += 1 << shift) {
                    for (int z = section.zmin, zm = section.zmax; z < zm; z += 1 << shift) {
                        long chunkPos = ((long) (x >>> shift) << 42) | ((long) y >>> shift) << 21 | z >>> shift;
                        Collection<V> list = map.get(chunkPos);
                        if (list != null) {
                            for (V data : list) {
                                if (data.getSection().intersects(section))
                                    return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public Collection<V> get(int world, Section section) {
        LongMap<Collection<V>> map = fastPath.get(world);
        if (map != null) {
            LinkedList<V> result = new LinkedList<>();
            for (int x = section.xmin, xm = section.xmax; x < xm; x += 1 << shift) {
                for (int y = section.ymin, ym = section.ymax; y < ym; y += 1 << shift) {
                    for (int z = section.zmin, zm = section.zmax; z < zm; z += 1 << shift) {
                        long chunkPos = ((long) (x >>> shift) << 42) | ((long) y >>> shift) << 21 | z >>> shift;
                        Collection<V> list = map.get(chunkPos);
                        if (list != null) {
                            for (V data : list) {
                                if (data.getSection().intersects(section))
                                    result.add(data);
                            }
                        }
                    }
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    public void put(V data) {
        data.handle(this);
        LongMap<Collection<V>> map = fastPath.get(data.getWorld());
        if (map == null) {
            fastPath.put(data.getWorld(), map = new LongMap<>());
        }
        Section sect = data.getSection();
        for (long x = sect.xmin >> shift, xend = sect.xmax >> shift; x <= xend; x++) {
            for (long y = sect.ymin >> shift, yend = sect.ymax >> shift; y <= yend; y++) {
                for (long z = sect.zmin >> shift, zend = sect.zmax >> shift; z <= zend; z++) {
                    long chunkPos = ((x >>> shift) << 42) | (y >>> shift) << 21 | z >>> shift;
                    Collection<V> list = map.get(chunkPos);
                    if (list == null) {
                        map.put(data.getWorld(), list = newStorage());
                    }
                    list.add(data);
                }
            }
        }
    }

    protected Collection<V> newStorage() {
        return new MyHashSet<>(2);
    }

    public void remove(V data) {
        LongMap<Collection<V>> worldMap = fastPath.get(data.getWorld());
        if (worldMap != null) {
            Section sect = data.getSection();
            for (long x = sect.xmin >> shift, xend = sect.xmax >> shift; x <= xend; x++) {
                for (long y = sect.ymin >> shift, yend = sect.ymax >> shift; y <= yend; y++) {
                    for (long z = sect.zmin >> shift, zend = sect.zmax >> shift; z <= zend; z++) {
                        long chunkPos = ((x >>> shift) << 42) | (y >>> shift) << 21 | z >>> shift;
                        Collection<V> list = worldMap.get(chunkPos);
                        if (list != null) {
                            list.remove(data);
                            if (list.isEmpty()) {
                                worldMap.remove(chunkPos);
                            }
                        }
                    }
                }
            }
        }
    }

    public void clear() {
        for (LongMap<Collection<V>> map : fastPath.values()) {
            map.clear();
        }
    }

    public Collection<V> getByShifted(int world, long off) {
        LongMap<Collection<V>> map = fastPath.get(world);
        return map == null ? Collections.emptyList() : map.getOrDefault(off, Collections.emptyList());
    }

    public void putAll(FastPath<V> fastPath) {
        for (IntMap.Entry<LongMap<Collection<V>>> entry0 : fastPath.fastPath.entrySet()) {
            LongMap<Collection<V>> map = this.fastPath.get(entry0.getKey());
            if (map == null) {
                this.fastPath.put(entry0.getKey(), map = new LongMap<>());
            }

            for (LongMap.Entry<Collection<V>> entry1 : entry0.getValue().entrySet()) {
                Collection<V> copy = newStorage();
                copy.addAll(entry1.getValue());

                map.put(entry1.getKey(), entry1.getValue());
            }
        }
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     @Override public Iterator<V> iterator() {
     return new AbstractIterator<V>() {
     @Override public boolean computeNext() {
     return false;
     }
     };
     }*/
}
