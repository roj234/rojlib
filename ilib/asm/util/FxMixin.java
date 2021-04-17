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
package ilib.asm.util;

import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstType;
import roj.asm.cst.CstUTF;
import roj.asm.tree.ConstantData;
import roj.io.IOUtil;

import java.io.IOException;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/30 15:45
 */
public class FxMixin {
    public static byte[] code;

    static {
        try {
            code = getCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] getCode() throws IOException {
        ConstantData data = Parser.parseConstants(IOUtil.read("ilib/asm/util/mixin/Proxy.class"));
        List<Constant> array = data.cp.array();
        for (int i = 0; i < array.size(); i++) {
            Constant c = array.get(i);
            if (c.type() == CstType.UTF) {
                CstUTF u = (CstUTF) c;
                final String s = u.getString();
                if (s.length() >= 25) {
                    u.setString(s.replace("ilib/asm/util/mixin/NVE_1",
                                          "org/spongepowered/asm/mixin/transformer/MixinTransformer"));
                    u.setString(u.getString()
                                 .replace("ilib/asm/util/mixin/Proxy",
                                          "org/spongepowered/asm/mixin/transformer/Proxy"));
                }
            }
        }
        return Parser.toByteArray(data);
    }
}
