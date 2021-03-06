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

import net.minecraftforge.fml.common.MetadataCollection;
import roj.asm.util.ConstantPool;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.text.StringPool;
import roj.util.ByteList;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class JarInfo {
    public final Map<String, ClassInfo> classes;
    public final List<String> mainClasses;
    public MetadataCollection mc;

    public JarInfo() {
        this(new MyHashMap<>(), new SimpleList<>());
    }

    public JarInfo(Map<String, ClassInfo> classes, List<String> mainClasses) {
        this.classes = classes;
        this.mainClasses = mainClasses;
    }

    public void toByteArray(ByteList w, StringPool pool, ConstantPool cw) {
        w.putVarInt(classes.size(), false);
        for (Map.Entry<String, ClassInfo> entry : classes.entrySet()) {
            pool.writeString(w, entry.getKey());
            entry.getValue().toByteArray(w, pool, cw);
        }
        w.putVarInt(mainClasses.size(), false);
        for (String s : mainClasses) {
            pool.writeString(w, s);
        }
    }

    public static JarInfo fromByteArray(ByteList r, StringPool pool, ConstantPool cp) {
        int len = r.readVarInt(false);
        Map<String, ClassInfo> map = new MyHashMap<>(len);
        for (int i = 0; i < len; i++) {
            map.put(pool.readString(r), ClassInfo.fromByteArray(r, pool, cp));
        }
        len = r.readVarInt(false);
        List<String> list = new SimpleList<>(len);
        for (int i = 0; i < len; i++) {
            list.add(pool.readString(r));
        }
        return new JarInfo(map, list);
    }

    @Override
    public String toString() {
        return "JarInfo{" +
                "classes=" + classes +
                ", mainClasses=" + mainClasses +
                '}';
    }
}
