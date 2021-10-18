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
package ilib.asm.fasterforge.anc;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import roj.asm.tree.anno.Annotation;
import roj.asm.util.ConstantPool;
import roj.text.StringPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51java
 */
public class ClassInfo {
    public final String internalName;
    public final List<String> interfaces;
    public final Multimap<String, Annotation> annotations;

    public ClassInfo(String internalName) {
        this.internalName = internalName;
        this.interfaces = new ArrayList<>();
        this.annotations = ArrayListMultimap.create();
    }

    public ClassInfo(String internalName, List<String> interfaces, Multimap<String, Annotation> annotations) {
        this.internalName = internalName;
        this.interfaces = interfaces;
        this.annotations = annotations;
    }

    public void toByteArray(ByteWriter w, StringPool pool, ConstantPool cw) {
        pool.writeString(w, internalName);
        w.writeVarInt(interfaces.size(), false);
        for (String s : interfaces) {
            pool.writeString(w, s);
        }
        w.writeVarInt(annotations.size(), false);
        for (Map.Entry<String, Collection<Annotation>> entry : annotations.asMap().entrySet()) {
            pool.writeString(w, entry.getKey());
            Collection<Annotation> as = entry.getValue();
            w.writeVarInt(as.size(), false);
            for (Annotation annotation : as) {
                annotation.toByteArray(cw, w);
            }
        }
    }

    public static ClassInfo fromByteArray(ByteReader r, StringPool pool, ConstantPool cp) {
        String internalName = pool.readString(r);
        int len = r.readVarInt(false);
        List<String> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            list.add(pool.readString(r));
        }
        len = r.readVarInt(false);
        Multimap<String, Annotation> map = ArrayListMultimap.create(len, 1);
        for (int i = 0; i < len; i++) {
            String key = pool.readString(r);
            int len2 = r.readVarInt(false);
            for (int j = 0; j < len2; j++) {
                map.put(key, Annotation.deserialize(cp, r));
            }
        }
        return new ClassInfo(internalName, list, map);
    }

    @Override
    public String toString() {
        return "ClassInfo{" +
                "internalName='" + internalName + '\'' +
                ", interfaces=" + interfaces +
                ", annotations=" + annotations +
                '}';
    }
}
