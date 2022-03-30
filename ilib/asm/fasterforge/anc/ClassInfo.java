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

import roj.asm.tree.anno.Annotation;
import roj.asm.util.ConstantPool;
import roj.collect.MyHashMap;
import roj.text.StringPool;
import roj.util.ByteList;
import roj.util.Helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ClassInfo {
    public String internalName;
    public List<String> interfaces;
    public Map<String, List<Annotation>> annotations;

    public ClassInfo() {}

    public ClassInfo(String internalName) {
        this.internalName = internalName;
        this.interfaces = new ArrayList<>();
        this.annotations = new MyHashMap<>();
    }

    public ClassInfo(String internalName, List<String> interfaces, Map<String, List<Annotation>> annotations) {
        this.internalName = internalName;
        this.interfaces = interfaces;
        this.annotations = annotations;
    }

    public void toByteArray(ByteList w, StringPool pool, ConstantPool cw) {
        pool.writeString(w, internalName);
        w.putVarInt(interfaces.size(), false);
        for (int i = 0; i < interfaces.size(); i++) {
            pool.writeString(w, interfaces.get(i));
        }
        w.putVarInt(annotations.size(), false);
        System.out.println(internalName);
        System.out.println(annotations);
        for (Map.Entry<String, List<Annotation>> entry : annotations.entrySet()) {
            pool.writeString(w, entry.getKey());
            Collection<Annotation> as = entry.getValue();
            w.putVarInt(as.size(), false);
            for (Annotation annotation : as) {
                annotation.toByteArray(cw, w);
            }
        }
    }

    public static ClassInfo fromByteArray(ByteList r, StringPool pool, ConstantPool cp) {
        System.out.println("==============");
        String internalName = pool.readString(r);
        System.out.println("class= " + internalName);
        int len = r.readVarInt(false);
        List<String> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            list.add(pool.readString(r));
        }
        System.out.println("interface= " + list);
        len = r.readVarInt(false);
        Map<String, List<Annotation>> map = new MyHashMap<>();
        for (int i = 0; i < len; i++) {
            String key = pool.readString(r);
            System.out.println("annotation= " + key);
            int len2 = r.readVarInt(false);
            System.out.println("vlen=" + len2);
            System.out.println("rindexs=" + r.rIndex);
            for (int j = 0; j < len2; j++) {
                Annotation v = Annotation.deserialize(cp, r);
                System.out.println("ann[]=" + v);
                map.computeIfAbsent(key, Helpers.fnArrayList()).add(v);
            }
            System.out.println("rindexe=" + r.rIndex);
        }
        System.out.println("==============");
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
