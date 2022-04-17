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

package ilib.asm;

import ilib.Config;
import roj.asm.Parser;
import roj.asm.cst.CstUTF;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class ClassReplacer implements IClassTransformer {
    public static final  ClassReplacer INSTANCE = new ClassReplacer();
    private static final MyHashMap<String, byte[]> list = new MyHashMap<>();

    @Override
    public byte[] transform(String name, String trName, final byte[] basicClass) {
        if (!list.containsKey(trName))
            return basicClass;

        if ((Config.debug & 2) != 0) {
            Loader.logger.info("CL替换 " + trName + "(" + name + ')');
        }

        return list.remove(trName);
    }

    public static void add(String name, byte[] arr, String father) {
        ConstantData data = Parser.parseConstants(arr);

        data.parentCst.getValue().setString(father);

        CstUTF value = data.nameCst.getValue();

        String tmp;
        boolean eq = value.getString().equals(tmp = name.replace('.', '/'));
        value.setString(tmp);
        list.put(name, eq ? arr : Parser.toByteArray(data));
    }

    /**
     * using dot
     */
    public static void add(String name, byte[] arr) {
        ConstantData data = Parser.parseConstants(arr);
        CstUTF value = data.nameCst.getValue();

        String tmp;
        boolean eq = value.getString().equals(tmp = name.replace('.', '/'));
        value.setString(tmp);
        list.put(name, eq ? arr : Parser.toByteArray(data));
    }
}