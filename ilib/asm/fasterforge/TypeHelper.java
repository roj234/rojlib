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
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.type.ParamHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class TypeHelper {
    public static Map<String, Object> toPrimitive(Map<String, AnnVal> param) {
        Map<String, Object> map = new HashMap<>(param.size());
        for (Map.Entry<String, AnnVal> entry : param.entrySet()) {
            map.put(entry.getKey(), toPrimitive(entry.getValue()));
        }
        return map;
    }

    public static Object toPrimitive(AnnVal param) {
        switch (param.type()) {
            case AnnVal.FLOAT:
                return param.asFloat();
            case AnnVal.DOUBLE:
                return param.asDouble();
            case AnnVal.LONG:
                return param.asLong();
            case AnnVal.INT:
                return param.asInt();
            case AnnVal.STRING:
                return param.asString();
            case AnnVal.SHORT:
                return (short) param.asInt();
            case AnnVal.CHAR:
                return (char) param.asInt();
            case AnnVal.BYTE:
                return (byte) param.asInt();
            case AnnVal.BOOLEAN:
                return param.asInt() == 1;
            case AnnVal.ENUM:
                AnnValEnum ave = param.asEnum();
                return new ModAnnotation.EnumHolder(ave.clazz, ave.value);
            case AnnVal.ARRAY:
                List<AnnVal> value = param.asArray();
                List<Object> list = new ArrayList<>(value.size());
                for (int i = 0; i < value.size(); i++) {
                    list.add(toPrimitive(value.get(i)));
                }
                return list;
            case AnnVal.CLASS:
                return Type.getType(ParamHelper.getField(param.asClass()));
            case AnnVal.ANNOTATION:
                return toPrimitive(param.asAnnotation().values);
        }
        throw new UnsupportedOperationException();
    }

    public static Type asmType(String rawName) {
        return Type.getType("L" + rawName + ";");
    }
}
