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
package ilib.asm.fasterforge;

import roj.asm.Parser;
import roj.asm.mapper.CodeMapper;
import roj.asm.mapper.ConstMapper;
import roj.asm.mapper.util.Context;
import roj.collect.TrieTreeSet;
import roj.util.ByteList;

import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;

import java.util.Arrays;
import java.util.List;

public class DeobfuscationTransformer implements IClassTransformer, IClassNameTransformer {
    private static final TrieTreeSet EXEMPT_LIBS = new TrieTreeSet("com.google.", "com.mojang.", "joptsimple.", "io.netty.", "it.unimi.dsi.fastutil.", "oshi.", "com.sun.", "com.ibm.", "paulscode.", "com.jcraft");

    private final boolean deobfuscatedEnvironment = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");

    private final ConstMapper   mapper;
    private final CodeMapper    codeMapper;
    private final List<Context> ctxs;

    public DeobfuscationTransformer() {
        this.mapper = ((IFDAccessPort)FMLDeobfuscatingRemapper.INSTANCE).getMapper();
        this.codeMapper = new CodeMapper(mapper);
        this.ctxs = Arrays.asList(new Context("", null));
    }

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null)
            return null;
        if (!shouldTransform(name))
            return bytes;
        ctxs.get(0).set(new ByteList(bytes));
        mapper.remapIncrement(ctxs);
        codeMapper.remap(true, ctxs);
        return Parser.toByteArray(Parser.parse(ctxs.get(0).get()));
    }

    private boolean shouldTransform(String name) {
        if(EXEMPT_LIBS.startsWith(name))
            return false;
        if (this.deobfuscatedEnvironment) {
            return !name.startsWith("net.minecraft.") && !name.startsWith("net.minecraftforge.");
        }
        return true;
    }

    public String remapClassName(String name) { return FMLDeobfuscatingRemapper.INSTANCE.map(name.replace('.', '/')).replace('/', '.'); }

    public String unmapClassName(String name) { return FMLDeobfuscatingRemapper.INSTANCE.unmap(name.replace('.', '/')).replace('/', '.'); }
}
