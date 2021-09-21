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
package roj.math;

import roj.collect.AbstractIterator;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Mh Position
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/16 19:36
 */
public class MhPosition {
    static final class MhItr2 extends AbstractIterator<Vec2i> implements Iterable<Vec2i> {
        private final Vec2i hp = new Vec2i();
        private final int x, y, r;
        MhItr2(int x, int z, int radius) {
            result = hp;
            this.x = x;
            this.y = z;
            this.r = radius;
        }
        private int s0, dr, d;

        @Override
        public boolean computeNext() {
            Vec2i hp = this.hp;
            if((s0 & 8) == 0) {
                hp.x = x;
                hp.y = y;
                s0 |= 12;
                dr = 0;
                return true;
            }
            if((s0 & 4) != 0) {
                if (++dr >= r) {
                    return false;
                }
                s0 &= 8;
                hp.y = y - dr;
                d = x - dr;
            }
            int r = this.dr, e;

            switch (s0 & 3) {
                case 0:
                    e = x + r;
                    if(d < e) {
                        hp.x = d++;
                        break;
                    } else {
                        s0++;
                        hp.x = e;
                        d = y - r;
                    }
                case 1:
                    e = y + r;
                    if(d < e) {
                        hp.y = d++;
                        break;
                    } else {
                        s0++;
                        hp.y = e;
                        d = x + r;
                    }
                case 2:
                    e = x - r;
                    if(d > e) {
                        hp.x = d--;
                        break;
                    } else {
                        s0++;
                        hp.x = e;
                        d = y + r;
                    }
                case 3:
                    e = y - r;
                    if(d > e) {
                        hp.y = d--;
                        break;
                    } else {
                        s0++;
                        return computeNext();
                    }
            }
            return true;
        }

        @Nonnull
        @Override
        public Iterator<Vec2i> iterator() {
            return this;
        }

        @Override
        public boolean reset() {
            s0 = 0;
            stage = INITIAL;
            return true;
        }

        @Override
        public void forEach(Consumer<? super Vec2i> action) {
            Vec2i hp = this.hp;
            hp.x = x;
            hp.y = y;
            action.accept(hp);
            int e1;
            for (int r = 1; r <= this.r; r++) {
                hp.y = y - r;

                e1 = x + r;
                for (int dx = x - r; dx < e1; dx++) {
                    hp.x = dx;
                    action.accept(hp);
                }
                hp.x = e1;

                e1 = y + r;
                for (int dy = y - r; dy < e1; dy++) {
                    hp.y = dy;
                    action.accept(hp);
                }
                hp.y = e1;

                e1 = x - r;
                for (int dx = x + r; dx > e1; dx--) {
                    hp.x = dx;
                    action.accept(hp);
                }
                hp.x = e1;

                e1 = y - r;
                for (int dy = y + r; dy > e1; dy--) {
                    hp.y = dy;
                    action.accept(hp);
                }
            }
        }
    }

    static final class MhItr3 extends AbstractIterator<Vec3i> implements Iterable<Vec3i> {
        private final Vec3i hp = new Vec3i();
        private final int x, y, z, r;
        MhItr3(int x, int y, int z, int r) {
            result = hp;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
        }

        @Override
        public boolean computeNext() {
            return false;
        }

        @Nonnull
        @Override
        public Iterator<Vec3i> iterator() {
            throw new UnsupportedOperationException("Not implement yet, use forEach() instead");
            // todo
            //return this;
        }

        @Override
        public void forEach(Consumer<? super Vec3i> c) {
            Vec3i hp = this.hp;
            hp.x = x;
            hp.y = y;
            hp.z = z;
            c.accept(hp);
            int r = 1;
            while (r <= this.r) {
                hp.z = z - r;
                c.accept(hp);
                hp.z = z + r;
                c.accept(hp);
                int r1 = 0;
                while (r1 <= r) {
                    hp.z = z - r;
                    fxy(r1, c);
                    hp.x = x;
                    hp.y = y;
                    hp.z = z + r;
                    fxy(r1++, c);
                }

                hp.x = x - r;
                hp.y = y;
                hp.z = z;
                c.accept(hp);
                fyz(r - 1, c);
                hp.x = x + r;
                hp.y = y;
                hp.z = z;
                c.accept(hp);
                fyz(r - 1, c);

                hp.x = x;
                hp.y = y - r;
                hp.z = z;
                c.accept(hp);
                fxz(r - 1, c);
                hp.x = x;
                hp.y = y + r;
                hp.z = z;
                c.accept(hp);
                fxz(r - 1, c);
                hp.y = y + r;

                for (int z = this.z - r + 1; z < this.z + r; z++) {
                    hp.z = z;
                    // x- y+
                    hp.x = x - r;
                    c.accept(hp);
                    // x- y-
                    hp.y = y - r;
                    c.accept(hp);
                    // x+ y-
                    hp.x = x + r;
                    c.accept(hp);
                    // x+ y+
                    hp.y = y + r;
                    c.accept(hp);
                }
                hp.x = x;
                hp.y = y;
                r++;
            }
        }

        private void fxy(int r, Consumer<? super Vec3i> c) {
            Vec3i hp = this.hp;
            hp.y = y - r;

            int e1 = x + r;
            for (int dx = x - r; dx < e1; dx++) {
                hp.x = dx;
                c.accept(hp);
            }
            hp.x = e1;

            e1 = y + r;
            for (int dy = y - r; dy < e1; dy++) {
                hp.y = dy;
                c.accept(hp);
            }
            hp.y = e1;

            e1 = x - r;
            for (int dx = x + r; dx > e1; dx--) {
                hp.x = dx;
                c.accept(hp);
            }
            hp.x = e1;

            e1 = y - r;
            for (int dy = y + r; dy > e1; dy--) {
                hp.y = dy;
                c.accept(hp);
            }
        }

        private void fxz(int r, Consumer<? super Vec3i> c) {
            Vec3i hp = this.hp;
            hp.z = z - r;

            int e1 = x + r;
            for (int dx = x - r; dx < e1; dx++) {
                hp.x = dx;
                c.accept(hp);
            }
            hp.x = e1;

            e1 = z + r;
            for (int dz = z - r; dz < e1; dz++) {
                hp.z = dz;
                c.accept(hp);
            }
            hp.z = e1;

            e1 = x - r;
            for (int dx = x + r; dx > e1; dx--) {
                hp.x = dx;
                c.accept(hp);
            }
            hp.x = e1;

            e1 = z - r;
            for (int dz = z + r; dz > e1; dz--) {
                hp.z = dz;
                c.accept(hp);
            }
        }

        private void fyz(int r, Consumer<? super Vec3i> c) {
            Vec3i hp = this.hp;
            hp.y = y - r;

            int e1 = z + r;
            for (int dz = z - r; dz < e1; dz++) {
                hp.z = dz;
                c.accept(hp);
            }
            hp.z = e1;

            e1 = y + r;
            for (int dy = y - r; dy < e1; dy++) {
                hp.y = dy;
                c.accept(hp);
            }
            hp.y = e1;

            e1 = z - r;
            for (int dz = z + r; dz > e1; dz--) {
                hp.z = dz;
                c.accept(hp);
            }
            hp.z = e1;

            e1 = y - r;
            for (int dy = y + r; dy > e1; dy--) {
                hp.y = dy;
                c.accept(hp);
            }
        }
    }

    /**
     *  路径迭代？
     *  ====================>
     * /|\                  |
     *  |                   |
     *  |                   |
     *  |         C         |
     *  |                   |
     *  |                  \|/
     *  <====================
     */
    public static Iterable<Vec2i> mh2d(int x, int z, int radius) {
        return new MhItr2(x, z, radius);
    }

    public static Iterable<Vec3i> mh3d(int x, int y, int z, int radius) {
        return new MhItr3(x, y, z, radius);
    }
}
