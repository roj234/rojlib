/*
 * This file is a part of MoreItems
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
package lac.injector.misc;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.Clazz;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.NPInsnNode;
import roj.asm.util.AccessFlag;
import roj.io.IOUtil;
import roj.reflect.DirectAccessor;

import java.io.IOException;
import java.util.List;

/**
 * Dump class bytes
 *
 * @author Roj233
 * @since 2021/10/16 0:36
 */
public final class EWDump {
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            byte[] data = IOUtil.read("lac/injector/misc/ExitWhooshly.class");
            Clazz clz = Parser.parse(data);
            List<Method> methods = clz.methods;
            for (int i = 0; i < methods.size(); i++) {
                Method m = methods.get(i);
                m.attributes.clear();
                m.code.attributes.clear();
            }
            ConstantData cData = Parser.parseConstants(Parser.toByteArrayShared(clz));
            cData.nameCst.getValue().setString("net/minecraft/chunk/AsyncChunkLoader");
            dumpByteArray(Parser.toByteArray(cData));
        } else {
            Clazz c = new Clazz(52 << 16, AccessFlag.PUBLIC | AccessFlag.SUPER_OR_SYNC, "$", DirectAccessor.MAGIC_ACCESSOR_CLASS);
            Method init = new Method(AccessFlag.STATIC, c, "<clinit>", "()V");
            init.code = new AttrCode(init);
            init.code.instructions.add(NPInsnNode.of(Opcodes.LADD));
            c.methods.add(init);
            byte[] bytes = c.getBytes().toByteArray();
            dumpByteArray(bytes);
            System.out.println(bytes.length);
        }
    }

    private static void dumpByteArray(byte[] data) {
        StringBuilder sb = new StringBuilder().append("new byte[] {");
        for (byte datum : data) {
            sb.append(datum).append(',');
        }
        sb.setCharAt(sb.length() - 1, '}');
        System.out.println(sb.append(';').toString());
        System.out.println();
    }
}
