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

package ilib.asm.transformers;

import ilib.Config;
import ilib.asm.Loader;
import net.minecraft.launchwrapper.IClassTransformer;
import roj.asm.Parser;
import roj.asm.cst.CstUTF;
import roj.asm.struct.ConstantData;
import roj.collect.MyHashMap;
import roj.util.log.ILogger;
import roj.util.log.LogManager;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public class ClassReplacer implements IClassTransformer {
    static final ILogger logger = LogManager.getLogger("ClassReplacer");

    public static final ClassReplacer INSTANCE = new ClassReplacer();
    private static MyHashMap<String, byte[]> list = new MyHashMap<>();

    @Override
    public byte[] transform(String name, String transed, final byte[] basicClass) {
        Loader.tryPatch(this);
        if (list == null || !list.containsKey(transed))
            return basicClass;

        if ((Config.debug & 2) != 0)
            logger.debug("Replaced class " + transed + "(" + name + ')');

        byte[] arr = list.remove(transed);

        if (list.isEmpty())
            list = null;

        return arr;
    }


    public static void addClass(String name, byte[] arr, String father) {
        if (list != null) {
            ConstantData data = Parser.parseConstants(arr);

            data.parentCst.getValue().setString(father);

            CstUTF value = data.nameCst.getValue();

            String tmp;
            boolean eq = value.getString().equals(tmp = name.replace('.', '/'));
            value.setString(tmp);
            list.put(name, eq ? arr : Parser.toByteArray(data));
        } else
            logger.warn("Time too late!");
    }

    /**
     * using dot
     */
    public static void addClass(String name, byte[] arr) {
        if (list != null) {
            ConstantData data = Parser.parseConstants(arr);
            CstUTF value = data.nameCst.getValue();

            String tmp;
            boolean eq = value.getString().equals(tmp = name.replace('.', '/'));
            value.setString(tmp);
            list.put(name, eq ? arr : Parser.toByteArray(data));
        } else
            logger.warn("Time too late!");
    }
}