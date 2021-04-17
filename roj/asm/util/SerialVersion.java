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
package roj.asm.util;

import roj.asm.tree.Field;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.NativeType;
import roj.asm.type.Type;
import roj.collect.BSLowHeap;
import roj.util.ByteList;
import roj.util.ByteWriter;
import roj.util.Helpers;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;

import static roj.asm.util.AccessFlag.*;
/**
 * 计算SerialVersionUID
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/29 1:58
 */
public class SerialVersion {
    static final Comparator<Item> ITEM_SORTER = (o1, o2) -> {
        int v = o1.name.compareTo(o2.name);
        if (v != 0) return v;
        return o1.desc.compareTo(o2.desc);
    };
    static final AtomicReference<MessageDigest> LOCAL_SHA = new AtomicReference<>();

    public static boolean computeIfNotPresent(IClass cz) {
        int index = cz.getFieldByName("serialVersionUID");
        if(index != -1 && cz.fields().get(index).rawDesc().equals("J")) {
            return false;
        }

        BSLowHeap<Item> constructors = new BSLowHeap<>(ITEM_SORTER);
        BSLowHeap<Item> methods = new BSLowHeap<>(ITEM_SORTER);

        boolean clInit = false;
        for (MoFNode node : cz.methods()) {
            String name = node.name();
            if ("<clinit>".equals(name)) {
                clInit = true;
                continue;
            }

            int access = node.accessFlag2() &
                    (PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | SUPER_OR_SYNC | NATIVE | ABSTRACT | STRICTFP);
            if ((access & PRIVATE) == 0) {
                ("<init>".equals(name) ? constructors : methods).add(new Item(name, node.rawDesc(), access));
            }
        }

        ByteWriter w = new ByteWriter(128);
        w.writeJavaUTF(cz.className().replace('/', '.'));
        int access = cz.accessFlag().flag;
        if ((access & INTERFACE) != 0) {
            access = methods.size() > 0 ? access | ABSTRACT : access & -1025;
        }

        w.writeInt(access & (PUBLIC | FINAL | INTERFACE | ABSTRACT));
        String[] itf = cz.interfaces().toArray(new String[cz.interfaces().size()]);
        Arrays.sort(itf);
        for (String s : itf) {
            w.writeJavaUTF(s.replace('/', '.'));
        }

        BSLowHeap<Item> fields = new BSLowHeap<>(ITEM_SORTER);
        for (MoFNode node : cz.fields()) {
            access = node.accessFlag2();
            if ((access & PRIVATE) == 0 || (access & (STATIC | TRANSIENT_OR_VARARGS)) == 0) {
                access &= (PUBLIC | PROTECTED | STATIC | FINAL | VOLATILE_OR_BRIDGE);
                fields.add(new Item(node.name(), node.rawDesc(), access));
            }
        }
        for(int i = 0; i < fields.size(); ++i) {
            w.writeJavaUTF(fields.get(i).name);
            w.writeInt(fields.get(i).access)
             .writeJavaUTF(fields.get(i).desc);
        }
        if (clInit) {
            w.writeJavaUTF("<clinit>");
            w.writeInt(STATIC)
             .writeJavaUTF("()V");
        }

        for(int i = 0; i < constructors.size(); ++i) {
            w.writeJavaUTF(constructors.get(i).name);
            w.writeInt(constructors.get(i).access)
             .writeJavaUTF(constructors.get(i).desc.replace('/', '.'));
        }
        for(int i = 0; i < methods.size(); ++i) {
            w.writeJavaUTF(methods.get(i).name);
            w.writeInt(methods.get(i).access)
             .writeJavaUTF(methods.get(i).desc.replace('/', '.'));
        }

        ByteList bl = w.list;
        MessageDigest localSha = LOCAL_SHA.getAndSet(null);
        if(localSha == null) {
            try {
                localSha = MessageDigest.getInstance("SHA");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Unexpected: 不支持SHA1");
            }
            localSha.update(bl.list, 0, bl.pos());

            bl.clear();
            bl.ensureCapacity(20);
            bl.pos(8);
            try {
                localSha.digest(bl.list, 0, 20);
            } catch (DigestException e) {
                throw new RuntimeException("Unexpected: SHA1 Error", e);
            }
        }
        LOCAL_SHA.compareAndSet(null, localSha);

        long svuid = 0;
        for(int i = 7; i >= 0; --i) {
            svuid = svuid << 8 | (long)(bl.list[i] & 255);
        }

        Field fl = new Field(new FlagList(STATIC | FINAL), "serialVersionUID", Type.std(NativeType.LONG));
        cz.fields().add(Helpers.cast(fl));
        return true;
    }

    static final class Item {
        String name, desc;
        char access;

        public Item(String name, String desc, int access) {
            this.name = name;
            this.desc = desc;
            this.access = (char) access;
        }
    }
}
