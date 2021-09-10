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
package roj.misc;

import roj.collect.SimpleList;
import roj.math.MathUtils;
import roj.math.MutableInt;
import roj.util.MyRandom;

import java.util.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ReactionTest {
    private abstract static class C implements Comparable<C> {
        public C(int c) {
            this.c = c;
        }

        int c;

        @Override
        public boolean equals(Object o) {
            return o.getClass() == getClass() && ((C) o).c == c;
        }

        @Override
        public abstract int hashCode();

        @Override
        public int compareTo(C c) {
            return Integer.compare(hashCode(), c.hashCode());
        }

        public abstract C copy();
    }

    private static class C1 extends C {
        public C1(int c) {
            super(c);
            if (c < 1)
                throw new IllegalArgumentException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public C copy() {
            return new C1(c);
        }

        @Override
        public String toString() {
            return appendCN(new StringBuilder(), c).append('烷').toString();
        }

        public boolean split(Random random, List<C1> c1List, List<C2> c2List, List<C> not) {
            int able = c - 2; // 100烷 -> 3 * 20烯 + 2 * 20 烷
            if (able <= 0)
                throw new IllegalArgumentException(toString());

            int c2 = (int) Math.round(gaussianIn(random, 0, able + 1));
            if (c2 < 2)
                return false;
            int c1 = this.c - c2;

            if (c1 <= 2) {
                not.add(new C1(c1));
            } else {
                c1List.add(new C1(c1));
            }

            c2List.add(new C2(c2));

            this.c = -1;

            return true;
        }

        @Override
        public int hashCode() {
            return c;
        }
    }

    private static class C2 extends C {
        public C2(int c) {
            super(c);
            if (c < 2)
                throw new IllegalArgumentException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public C copy() {
            return new C2(c);
        }

        public boolean merge(Random random, List<C1> c1List, List<C> result, MutableInt hydrogen) {
            if (hydrogen.getValue() > 0 && random.nextBoolean()) {
                hydrogen.subtract(2);
                if (this.c > 2)
                    c1List.add(new C1(this.c));
                else
                    result.add(new C1(this.c));
                return true;
            }
            if (c1List.isEmpty() && result.isEmpty()) return false;
            boolean flag = random.nextInt(c1List.size() + result.size()) >= result.size();
            if (!c1List.isEmpty()) {
                if (result.isEmpty()) {
                    if (!flag)
                        return false;
                }
            } else {
                if (flag)
                    return false;
            }


            C1 c1;
            if (flag) {
                c1 = c1List.get(random.nextInt(c1List.size()));
            } else {
                C c;
                int id;
                do {
                    c = result.get(id = random.nextInt(result.size()));
                } while (!(c instanceof C1));
                c1 = (C1) c;
                result.remove(id);
                c1List.add(c1);
            }

            c1.c += this.c;
            return true;
        }

        @Override
        public int hashCode() {
            return c << 16;
        }

        @Override
        public String toString() {
            return appendCN(new StringBuilder(), c).append('烯').toString();
        }
    }


    static final char[] ZERO_2_TEN_CN = new char[]{
            '甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸'
    };

    static StringBuilder appendCN(StringBuilder list, int count) {
        if (count <= 10)
            list.append(ZERO_2_TEN_CN[count - 1]);
        else {
            return MathUtils.toChinaString(list, count);
        }
        return list;
    }

    public static void main(String[] args) {
        C1 c100 = new C1(Integer.parseInt(args[0]));

        int cccc = Integer.parseInt(args[1]);

        int i = Integer.parseInt(args[2]);

        Random random = new MyRandom();

        SimpleList<C1> operable = new SimpleList<>();
        SimpleList<C1> operable1 = new SimpleList<>();

        SimpleList<C2> operable2 = new SimpleList<>();
        operable.capacityType = 2;
        operable1.capacityType = 2;
        operable2.capacityType = 2;

        MutableInt hyd = new MutableInt(args[3]);
        hyd.add(2);

        System.out.println("Input: " + c100 + " * " + args[1]);
        System.out.println("MaxRound: " + args[2]);
        System.out.println("H2: " + args[3]);

        for (int j = 0; j < cccc; j++) {
            operable.add((C1) c100.copy());
        }

        List<C> result = new ArrayList<>();

        int k = 0;
        while (!operable.isEmpty() && i > 0) {
            long time = System.currentTimeMillis();

            for (C1 c1 : operable) {
                if (myRandIf(random, c1.c, true)) {
                    if (!c1.split(random, operable1, operable2, result)) {
                        operable1.add(c1);
                    }
                } else {
                    operable1.add(c1);
                }
            }

            SimpleList<C1> tmp = operable;
            operable = operable1;
            operable1 = tmp;
            operable1.clear();

            Iterator<C2> c2Iterator = operable2.iterator();
            while (c2Iterator.hasNext()) {
                C2 c2 = c2Iterator.next();
                if (myRandIf(random, c2.c, false)) {
                    if (c2.merge(random, operable, result, hyd))
                        c2Iterator.remove();
                }
            }

            i--;
            k++;

            System.out.println("Round " + k + " completed, time cost " + ((System.currentTimeMillis() - time) / 1000) + "s");
            System.out.println("C1 " + operable.size() + ", C2 " + operable2.size());
        }
        result.addAll(operable);
        result.addAll(operable2);
        operable.clear();
        operable2.clear();

        Map<C, Integer> counts = new TreeMap<>();
        double sum = result.size();
        int sumC = 0;

        for (C c : result) {
            Integer j = counts.computeIfAbsent(c, (c1) -> 0);
            counts.put(c, ++j);
            sumC += c.c;
        }

        System.out.println("Result: ");
        System.out.println("Sum: " + sum);
        System.out.println("SumC: " + sumC);
        System.out.println("Round: " + k);
        for (Map.Entry<C, Integer> entry : counts.entrySet()) {
            Integer val = entry.getValue();
            StringBuilder sb = new StringBuilder(entry.getKey().toString()).append(": ").append(val).append(" (");
            int startLen = sb.length();
            sb.append(((double) (val * 100)) / sum);

            if (sb.length() - startLen > 5) {
                sb.delete(startLen + 5, sb.length());
            }
            System.out.println(sb.append("%)"));
        }
    }

    private static boolean myRandIf(Random random, int o, boolean up) {
        int k1 = random.nextInt((int) Math.pow(o, 1.5));
        int k2 = random.nextInt(o);
        return up == (k1 > k2);
    }

    private static double gaussianIn(Random random, double min, double max) {
        double center = (min + max) / 2;
        double d = Math.sqrt(center) * random.nextGaussian() + center; // center at 1
        if (d < min)
            return min;
        if (d > max)
            return max;
        return d;
    }
}
