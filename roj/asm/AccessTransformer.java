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
import roj.asm.tree.attr.AttrInnerClasses;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
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
 * No description provided
 *
 * @author Roj234
 * @version 0.1
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

    public static void readAndParseAt(@Nonnull String ATConfig) {
        if (ATConfig.length() < 1)
            throw new RuntimeException("AT的配置文件已损坏");
        SimpleLineReader options = new SimpleLineReader(ATConfig);
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

    @Nonnull
    public static byte[] transform(@Nonnull String className, @Nonnull final byte[] bytecode) {
        Collection<String> list = transforms.get(className);
        if (list == null)
            return bytecode;
        return openSome(bytecode, list);
    }

    public static void doAT(FlagList list, boolean evenProtected) {
        list.remove(AccessFlag.PRIVATE | AccessFlag.FINAL);
        if (!evenProtected && list.hasAll(AccessFlag.PROTECTED)) {
            return;
        }
        list.remove(AccessFlag.PROTECTED);
        list.add(AccessFlag.PUBLIC);
    }


    public static byte[] openSubClass(final byte[] bytecode, Collection<String> names) {
        ConstantData data = Parser.parseConstants(bytecode);
        Attribute attribute = data.attrByName("InnerClasses");
        AttrInnerClasses ic = new AttrInnerClasses(Parser.reader(attribute), data.cp);
        data.attributes.putByName(ic);
        for (AttrInnerClasses.InnerClass innerClass : ic.classes) {
            if (names.contains(innerClass.self)) {
                FlagList list = new FlagList(innerClass.flags);
                list.remove(AccessFlag.FINAL | AccessFlag.PRIVATE | AccessFlag.PROTECTED);
                list.add(AccessFlag.PUBLIC);
                innerClass.flags = list.flag;
            }
        }
        return Parser.toByteArray(data);
    }

    public static byte[] openSome(final byte[] bytecode, Collection<String> names) {
        AccessData data = Parser.parseAccessDirect(bytecode);

        if (names.contains("<$extend>")) {
            FlagList list = data.accessFlag();
            list.remove(AccessFlag.FINAL | AccessFlag.PRIVATE | AccessFlag.PROTECTED);
            list.add(AccessFlag.PUBLIC);
            data.accessFlag(list);
        }

        boolean evenProtected = true;

        if (names.contains("*")) {
            names = new Universe();
            evenProtected = false;
        } else if (names.contains("*P")) {
            names = new Universe();
        }

        for (AccessData.MOF field : data.fields) {
            if (!names.contains(field.name) && !names.contains(field.name + '|' + field.desc))
                continue;
            FlagList list = field.accessFlag();
            doAT(list, evenProtected || !(names instanceof Universe));
            data.setFlagFor(field, list);
        }
        for (AccessData.MOF field : data.methods) {
            if (!names.contains(field.name) && !names.contains(field.name + '|' + field.desc))
                continue;
            FlagList list = field.accessFlag();
            doAT(list, evenProtected || !(names instanceof Universe));
            data.setFlagFor(field, list);
        }
        return data.toByteArray();
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