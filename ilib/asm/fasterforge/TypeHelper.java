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
import roj.asm.tree.anno.*;
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
                return ((AnnValFloat) param).value;
            case AnnVal.DOUBLE:
                return ((AnnValDouble) param).value;
            case AnnVal.LONG:
                return ((AnnValLong) param).value;
            case AnnVal.INT:
                return ((AnnValInt) param).value;
            case AnnVal.STRING:
                return ((AnnValString) param).value;
            case AnnVal.SHORT:
                return (short) ((AnnValInt) param).value;
            case AnnVal.CHAR:
                return (char) ((AnnValInt) param).value;
            case AnnVal.BYTE:
                return (byte) ((AnnValInt) param).value;
            case AnnVal.BOOLEAN:
                return ((AnnValInt) param).value == 1;
            case AnnVal.ENUM:
                AnnValEnum ave = (AnnValEnum) param;
                return new ModAnnotation.EnumHolder(ave.clazz, ave.value);
            case AnnVal.ARRAY:
                AnnValArray ava = (AnnValArray) param;
                List<Object> list = new ArrayList<>(ava.value.size());
                List<AnnVal> value = ava.value;
                for (int i = 0; i < value.size(); i++) {
                    list.add(toPrimitive(value.get(i)));
                }
                return list;
            case AnnVal.CLASS:
                AnnValClass avc = (AnnValClass) param;
                return Type.getType(ParamHelper.getField(avc.value));
            case AnnVal.ANNOTATION:
                return toPrimitive(((AnnValAnnotation) param).value.values);
        }
        throw new UnsupportedOperationException();
    }

    public static Type asmType(String rawName) {
        return Type.getType("L" + rawName + ";");
    }
}
