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
package ilib.asm.fasterforge.transformers;

import net.minecraft.launchwrapper.IClassTransformer;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrAnnotation;
import roj.asm.tree.attr.Attribute;

import java.lang.reflect.Modifier;

public class EventSubscriberTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null)
            return null;
        ConstantData cz = Parser.parseConstants(basicClass);
        boolean isSubscriber = false;
        for (MethodSimple mn : cz.methods) {
            Attribute attr = mn.attrByName("RuntimeVisibleAnnotations");
            if (attr == null) continue;
            AttrAnnotation annos = new AttrAnnotation(true, Parser.reader(attr), cz.cp);
            for (Annotation anno : annos.annotations) {
                if("net/minecraftforge/fml/common/eventhandler/SubscribeEvent".equals(anno.clazz)) {
                    if (Modifier.isPrivate(mn.accesses.flag)) {
                        String msg = "Cannot apply @SubscribeEvent to private method %s/%s%s";
                        throw new RuntimeException(String.format(msg, cz.name, mn.name.getString(), mn.type.getString()));
                    }
                    mn.accesses.flag = (char) toPublic(mn.accesses.flag);
                    isSubscriber = true;
                }
            }
        }
        if (isSubscriber) {
            cz.accesses.flag = (char) toPublic(cz.accesses.flag);
            return Parser.toByteArray(cz);
        }
        return basicClass;
    }

    private static int toPublic(int access) {
        return access & 0xFFFFFFF9 | 1;
    }
}
