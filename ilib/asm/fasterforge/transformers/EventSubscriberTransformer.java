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

import ilib.api.ContextClassTransformer;
import ilib.asm.Loader;
import roj.asm.Parser;
import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.MoFNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrAnnotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.Context;
import roj.collect.MyHashSet;

import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Modifier;
import java.util.List;

public final class EventSubscriberTransformer implements ContextClassTransformer {
    private static MyHashSet<String> has;

    public static void lockdown() {
        has = new MyHashSet<>();
        for (ASMDataTable.ASMData data : Loader.ASMTable.getAll(SubscribeEvent.class.getName())) {
            has.add(data.getClassName());
        }
    }

    @Override
    public void transform(String trName, Context ctx) {
        if (has != null && !has.contains(trName)) return;

        AccessData aad = ctx.isFresh() ? Parser.parseAccessDirect(ctx.get().list) : null;
        ConstantData cz = aad == null ? ctx.getData() : Parser.parseConstants(ctx.get().list);

        List<? extends MethodNode> methods = cz.methods;
        for (int j = 0; j < methods.size(); j++) {
            MoFNode m = methods.get(j);

            Attribute attr = m.attrByName("RuntimeVisibleAnnotations");
            if (attr == null) continue;

            List<Annotation> annos = AttrAnnotation.parse(cz.cp, Parser.reader(attr));
            for (int i = 0; i < annos.size(); i++) {
                Annotation ann = annos.get(i);
                if ("net/minecraftforge/fml/common/eventhandler/SubscribeEvent".equals(ann.clazz)) {
                    if (Modifier.isPrivate(m.accessFlag())) {
                        String msg = "Cannot apply @SubscribeEvent to private method %s/%s%s";
                        throw new RuntimeException(String.format(msg, cz.name, m.name(), m.rawDesc()));
                    }

                    if (aad != null) {
                        aad.methods.get(j).accessFlag(toPublic(m.accessFlag()));
                    } else {
                        m.accessFlag((char) toPublic(m.accessFlag()));
                    }
                    break;
                }
            }
        }

        if (aad != null) {
            aad.accessFlag(toPublic(cz.accesses));
        } else {
            cz.accesses = (char) toPublic(cz.accesses);
        }
    }

    private static int toPublic(int access) {
        return access & 0xFFFFFFF9 | 1;
    }
}
