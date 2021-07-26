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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import net.minecraft.launchwrapper.IClassTransformer;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrAnnotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.simple.MethodSimple;
import roj.util.ByteReader;

import java.lang.reflect.Modifier;

public class EventSubscriberTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null)
            return null;
        ConstantData classNode = Parser.parseConstants(basicClass, true);
        boolean isSubscriber = false;
        for (MethodSimple methodNode : classNode.methods) {
            Attribute attr = methodNode.attrByName("RuntimeVisibleAnnotations");
            if (attr == null) continue;
            AttrAnnotation anno = new AttrAnnotation(true, new ByteReader(attr.getRawData()), classNode.cp);
            if (Iterables.any(anno.annotations, SubscribeEventPredicate.INSTANCE)) {
                //System.out.println("Found");
                if (Modifier.isPrivate(methodNode.accesses.flag)) {
                    String msg = "Cannot apply @SubscribeEvent to private method %s/%s%s";
                    throw new RuntimeException(String.format(msg, classNode.name, methodNode.name.getString(), methodNode.type.getString()));
                }
                methodNode.accesses.flag = (short) toPublic(methodNode.accesses.flag);
                isSubscriber = true;
            }
        }
        if (isSubscriber) {
            classNode.accesses.flag = (short) toPublic(classNode.accesses.flag);
            return Parser.toByteArray(classNode);
        }
        return basicClass;
    }

    private static int toPublic(int access) {
        return access & 0xFFFFFFF9 | 1;
    }

    private static class SubscribeEventPredicate implements Predicate<Annotation> {
        static final SubscribeEventPredicate INSTANCE = new SubscribeEventPredicate();

        public boolean apply(Annotation input) {
            return "Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;".equals(input.rawDesc);
        }
    }
}
