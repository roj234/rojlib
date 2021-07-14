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
package ilib.animator.keyframe;

import roj.math.Mat4x3f;
import roj.text.StringPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/27 22:00
 */
public final class Keyframes implements Iterable<Keyframe> {
    List<Keyframe> keyframes = new ArrayList<>();
    static final Mat4x3f store = new Mat4x3f();

    private int binarySearch(int key) {
        int low = 0;
        int high = keyframes.size() - 1;

        List<Keyframe> a = keyframes;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = a.get(mid).tick;

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else
                return mid; // key found
        }

        // low ...

        return -(low + 1);  // key not found.
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    @Nonnull
    @Override
    public Iterator<Keyframe> iterator() {
        return keyframes.iterator();
    }

    public List<Keyframe> getInternal() {
        return this.keyframes;
    }

    public boolean contains(int index) {
        return index >= 0 && index < this.keyframes.size();
    }

    public Keyframe get(int index) {
        return this.keyframes.get(index);
    }

    public Keyframe remove(int index) {
        //flag &= ~1;
        return this.keyframes.remove(index);
    }

    public Mat4x3f interpolate(double tickPlusPt) {
        if (this.keyframes.isEmpty()) {
            return store.makeIdentity();
        }

        int i = binarySearch((int) tickPlusPt);
        if(i < 0)
            i = -i - 1;

        if(i == 0) {
            return keyframes.get(0);
        }

        if(i >= keyframes.size() - 1) {
            return keyframes.get(keyframes.size() - 1);
        }

        Keyframe curr = keyframes.get(i - 1),
        next = keyframes.get(i);

        return curr.interpolate(next, (tickPlusPt - curr.tick) / (next.tick - curr.tick), store);
    }

    public int add(Keyframe kf) {
        int i = binarySearch(kf.tick);
        if(i < 0)
            keyframes.add(i = -i - 1, kf);
        else
            keyframes.set(i, kf);
        return i;
    }

    public int applyTransform(int tick, byte type, float x, float y, float z) {
        int i = binarySearch(tick);
        if(i < 0) {
            i = -i - 1;
            Keyframe kf = new Keyframe();
            kf.tick = tick;
            kf.applyTransform(type, x, y, z);
            keyframes.add(i, kf);
        } else
            keyframes.get(i).applyTransform(type, x, y, z);

        return i;
    }

    static final Comparator<Keyframe> KFCP = (a, b) -> {
        return Integer.compare(a.tick, b.tick);
    };

    public void refresh(boolean force) {
        /*if (!this.keyframes.isEmpty() && (force || (flag & 1) == 0)) {
            keyframes.sort(KFCP);
        }
        flag |= 1;*/
    }

    public void copyFrom(Keyframes channel) {
        this.keyframes.clear();

        List<Keyframe> keyframes = channel.keyframes;
        for (int i = 0; i < keyframes.size(); i++) {
            this.keyframes.add(new Keyframe(keyframes.get(i)));
        }
    }

    public static Keyframes fromByteArray(ByteReader r, StringPool pool) {
        Keyframes kfs = new Keyframes();

        for (int i = 0, len = r.readVarInt(false); i < len; i++) {
            kfs.add(Keyframe.fromByteArray(r, pool));
        }
        kfs.refresh(false);

        return kfs;
    }

    public void toByteArray(ByteWriter w, StringPool pool) {
        w.writeVarInt(this.keyframes.size(), false);

        List<Keyframe> keyframes = this.keyframes;
        for (int i = 0; i < keyframes.size(); i++) {
            keyframes.get(i).toByteArray(w, pool);
        }
    }
}
