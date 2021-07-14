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
package ilib.asm.fasterforge;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.objectweb.asm.Type;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.RemapTo;
import roj.asm.nixim.Shadow;
import roj.asm.struct.anno.Annotation;
import roj.asm.type.ParamHelper;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
@Nixim("net.minecraftforge.fml.common.ModContainerFactory")
public class NiximModContainerFactory {
    @Shadow("modTypes")
    public static Map<Type, Constructor<? extends ModContainer>> modTypes;

    @Nullable
    @RemapTo("build")
    public ModContainer build(ASMModParser modParser, File modSource, ModCandidate container) {
        String className = modParser.getASMType().getClassName();
        LinkedList<Annotation> i = Helpers.cast(modParser.getAnnotations());

        Type type;
        Annotation ann;

        Iterator<Annotation> var5 = i.iterator();
        do {
            if (!var5.hasNext()) {
                return null;
            }

            ann = var5.next();
            type = Type.getType(ParamHelper.getField(ann.type));
        } while (!modTypes.containsKey(type));

        FMLLog.log.debug("Identified a mod of type {} ({}) - loading", type, className);

        try {
            ModContainer ret = modTypes.get(type).newInstance(className, container, TypeHelper.toPrimitive(ann.values));
            if (!ret.shouldLoadInEnvironment()) {
                FMLLog.log.debug("Skipping mod {}, container opted to not load.", className);
                return null;
            } else {
                return ret;
            }
        } catch (Exception var8) {
            FMLLog.log.error("Unable to construct {} container", type.getClassName(), var8);
            return null;
        }
    }
}
