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

import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.objectweb.asm.Type;
import roj.asm.tree.anno.AnnValArray;
import roj.asm.type.ParamHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static roj.asm.tree.anno.AnnotationType.*;

public abstract class TypeHelper {
    public static Map<String, Object> toPrimitive(Map<String, roj.asm.tree.anno.AnnVal> param) {
        Map<String, Object> map = new HashMap<>(param.size());
        for (Map.Entry<String, roj.asm.tree.anno.AnnVal> entry : param.entrySet()) {
            map.put(entry.getKey(), toPrimitive(entry.getValue()));
        }
        return map;
    }

    public static Object toPrimitive(roj.asm.tree.anno.AnnVal param) {
        switch (param.type) {
            case FLOAT:
                return ((roj.asm.tree.anno.AnnValFloat) param).value;
            case DOUBLE:
                return ((roj.asm.tree.anno.AnnValDouble) param).value;
            case LONG:
                return ((roj.asm.tree.anno.AnnValLong) param).value;
            case INT:
                return ((roj.asm.tree.anno.AnnValInt) param).value;
            case STRING:
                return ((roj.asm.tree.anno.AnnValString) param).value;
            case SHORT:
                return (short) ((roj.asm.tree.anno.AnnValInt) param).value;
            case CHAR:
                return (char) ((roj.asm.tree.anno.AnnValInt) param).value;
            case BYTE:
                return (byte) ((roj.asm.tree.anno.AnnValInt) param).value;
            case BOOLEAN:
                return ((roj.asm.tree.anno.AnnValInt) param).value == 1;
            case ENUM:
                roj.asm.tree.anno.AnnValEnum annotationValueEnum = (roj.asm.tree.anno.AnnValEnum) param;
                return new ModAnnotation.EnumHolder(annotationValueEnum.clazz.owner, annotationValueEnum.value);
            case ARRAY:
                roj.asm.tree.anno.AnnValArray annotationValueArray = (AnnValArray) param;
                List<Object> list = new ArrayList<>(annotationValueArray.value.size());
                annotationValueArray.value.forEach((c) -> list.add(toPrimitive(c)));
                return list;
            case CLASS:
                roj.asm.tree.anno.AnnValClass annotationValueClass = (roj.asm.tree.anno.AnnValClass) param;
                return Type.getType(ParamHelper.getField(annotationValueClass.value));
            case ANNOTATION:
                return toPrimitive(((roj.asm.tree.anno.AnnValAnnotation) param).value.values);
        }
        throw new UnsupportedOperationException();
    }

    public static Type asmType(String rawName) {
        return Type.getType("L" + rawName + ";");
    }
}
