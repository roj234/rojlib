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

package roj.asm;

import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.tree.attr.AttrInnerClasses.InnerClass;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttrHelper;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
public class AccessTransformer {
    private static final Map<String, Collection<String>> transforms = new MyHashMap<>();

    public static void readAndParseAt(@Nonnull File file) {
        try {
            readAndParseAt(IOUtil.readUTF(new FileInputStream(file)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse AT file: " + file.getName(), e);
        }
    }

    public static void readAndParseAt(@Nonnull Class<?> jarProvider, @Nonnull String fileName) {
        try {
            readAndParseAt(IOUtil.readUTF(jarProvider, fileName));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse AT file: " + fileName + " not found");
        }
    }

    public static void readAndParseAt(String cfg) {
        SimpleLineReader options = new SimpleLineReader(cfg);
        List<String> lst = new ArrayList<>(4);
        for (String conf : options) {
            if (conf.length() == 0) continue;
            if (conf.startsWith("public-f "))
                conf = conf.substring(9);
            lst.clear();
            TextUtil.split(lst, conf, ' ');
            if (lst.size() < 2) {
                System.err.println("Unknown entry " + conf);
                continue;
            }
            AccessTransformer.add(lst.get(0).replace('/', '.'), lst.get(1));
        }
    }

    public static byte[] transform(String className, byte[] b) {
        Collection<String> list = transforms.get(className);
        if (list == null) return b;
        return openSome(b, list);
    }

    public static int doAT(int flag, boolean evenProtected) {
        flag &= ~(AccessFlag.PRIVATE | AccessFlag.FINAL);
        if (!evenProtected && (flag & AccessFlag.PROTECTED) != 0) {
            return flag;
        }
        return (flag & ~AccessFlag.PROTECTED) | AccessFlag.PUBLIC;
    }

    public static byte[] openSubClass(final byte[] bytecode, Collection<String> names) {
        ConstantData data = Parser.parseConstants(bytecode);

        List<InnerClass> classes = AttrHelper.getInnerClasses(data.cp, data);
        if (classes == null) throw new IllegalStateException("InnerClass is null for " + data.name);
        for (int i = 0; i < classes.size(); i++) {
            InnerClass clz = classes.get(i);
            if (names.contains(clz.self)) {
                clz.flags = (char) doAT(clz.flags, true);
            }
        }
        return Parser.toByteArray(data);
    }

    public static byte[] openSome(byte[] bytecode, Collection<String> names) {
        AccessData data = Parser.parseAccessDirect(bytecode);
        openSome(names, data);
        return data.toByteArray();
    }

    public static void openSome(Collection<String> names, IClass data) {
        if (names.contains("<$extend>")) {
            data.accessFlag(doAT(data.accessFlag(), true));
        }

        boolean evenProtected = true;

        if (names.contains("*")) {
            names = new Universe();
            evenProtected = false;
        } else if (names.contains("*P")) {
            names = new Universe();
        }

        open(names, evenProtected, data.fields());
        open(names, evenProtected, data.methods());
    }

    private static void open(Collection<String> names, boolean evenProtected, List<? extends MoFNode> fields) {
        for (int i = 0; i < fields.size(); i++) {
            MoFNode field = fields.get(i);
            if (!names.contains(field.name()) && !names.contains(field.name() + '|' + field.rawDesc())) continue;
            int flag = doAT(field.accessFlag(), evenProtected || !(names instanceof Universe));
            field.accessFlag(flag);
        }
    }

    public static void add(String className, String fieldName) {
        transforms.computeIfAbsent(className, Helpers.fnMyHashSet()).add(fieldName);
    }

    public static Map<String, Collection<String>> getTransforms() {
        return transforms;
    }

    private static final class Universe extends AbstractCollection<String> {
        @Override
        public boolean contains(Object o) {
            return true;
        }

        @Nonnull
        @Override
        public Iterator<String> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }
    }
}