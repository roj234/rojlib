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

import ilib.asm.fasterforge.anc.FastParser;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.objectweb.asm.Type;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.asm.tree.anno.Annotation;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
@Nixim("net.minecraftforge.fml.common.ModContainerFactory")
public class NiximModContainerFactory {
    @Shadow("modTypes")
    public static Map<Type, Constructor<? extends ModContainer>> modTypes;

    @Nullable
    @Inject("build")
    public ModContainer build(ASMModParser modParser, File modSource, ModCandidate container) {
        String cn = modParser.getASMType().getClassName();
        List<Annotation> house = ((FastParser) modParser).getClassAnnotations();
        Type type;
        for (int i = 0; i < house.size(); i++) {
            Annotation ann = house.get(i);
            type = Type.getType("L" + ann.clazz + ';');
            if (modTypes.containsKey(type)) {
                FMLLog.log.debug("检测到 {} 类型的mod ({}) - 开始加载", type, cn);

                try {
                    ModContainer ret = modTypes.get(type).newInstance(cn, container, TypeHelper.toPrimitive(ann.values));
                    if (!ret.shouldLoadInEnvironment()) {
                        FMLLog.log.debug("放弃加载 {}, mod提示不应该在这个环境加载", cn);
                        return null;
                    } else {
                        return ret;
                    }
                } catch (Exception var8) {
                    FMLLog.log.error("无法构建mod容器 {}", type.getClassName(), var8);
                    return null;
                }
            }
        }
        return null;
    }
}
