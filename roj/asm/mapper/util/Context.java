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
package roj.asm.mapper.util;

import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.struct.ConstantData;
import roj.asm.struct.attr.AttrBootstrapMethods;
import roj.concurrent.Holder;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Context implements Holder<ByteList> {
    public static final int ID_METHOD = 0;
    public static final int ID_FIELD = 1;
    public static final int ID_CLASS = 2;
    public static final int ID_INVOKE_DYN = 3;

    public static final int LAMBDA_INDEX = 0;

    private String name;
    private ConstantData data;
    private Object stream;
    private ByteList result;

    private final ArrayList<Constant>[] typedTmp = Helpers.cast(new ArrayList<?>[4]);
    public AttrBootstrapMethods bsmCache;

    public Context(String name, Object o) {
        this.name = name;
        this.stream = o;
    }

    public ConstantData getData() {
        if(this.data == null) {
            ByteList bytes;
            if(stream != null) {
                bytes = read0(stream);
                stream = null;
            } else {
                bytes = this.result;
                this.result = null;
            }
            ConstantData data;
            try {
                data = Parser.parseConstants(bytes);
            } catch (Throwable e) {
                final File file = new File("ctx_" + (hashCode() ^ System.currentTimeMillis()) + ".err");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bytes.writeToStream(fos);
                } catch (IOException ignored) {}
                throw new IllegalArgumentException(name + " 读取失败", e);
            }
            this.data = data;
            getName();
        }
        return this.data;
    }

    private static ByteList read0(Object o) {
        if(o instanceof InputStream) {
            try (InputStream in = (InputStream) o) {
                return new ByteList(IOUtil.readFully(in));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (o instanceof ByteList) {
            return (ByteList) o;
        }
        throw new ClassCastException(o.getClass().getName());
    }

    private void initConstant() {
        if(typedTmp[0] == null) {
            typedTmp[0] = new ArrayList<>();
            typedTmp[1] = new ArrayList<>();
            typedTmp[2] = new ArrayList<>();
            typedTmp[3] = new ArrayList<>();
            List<Constant> csts = getData().writer.getConstants();
            for (int i = 0; i < csts.size(); i++) {
                Constant c = csts.get(i);
                switch (c.type()) {
                    case CstType.INTERFACE:
                    case CstType.METHOD:
                        typedTmp[ID_METHOD].add(c);
                        break;
                    case CstType.CLASS:
                        typedTmp[ID_CLASS].add(c);
                        break;
                    case CstType.FIELD:
                        typedTmp[ID_FIELD].add(c);
                        break;
                    case CstType.INVOKE_DYNAMIC:
                        typedTmp[ID_INVOKE_DYN].add(c);
                        break;
                }
            }
        }
    }

    public List<CstRef> getMethodConstants() {
        initConstant();
        return Helpers.cast(typedTmp[ID_METHOD]);
    }

    public List<CstRef> getFieldConstants() {
        initConstant();
        return Helpers.cast(typedTmp[ID_FIELD]);
    }

    public List<CstDynamic> getInvokeDynamic() {
        initConstant();
        return Helpers.cast(typedTmp[ID_INVOKE_DYN]);
    }

    public List<CstClass> getClassConstants() {
        initConstant();
        return Helpers.cast(typedTmp[ID_CLASS]);
    }

    @Override
    public ByteList get() {
        if(this.result == null) {
            if(this.data != null) {
                this.result = data.getBytes();
                clearData();
            } else {
                this.result = read0(stream);
                this.stream = null;
            }
        }
        return this.result;
    }

    private void clearData() {
        if(this.data != null) {
            getName();
            this.data = null;
            Arrays.fill(typedTmp, null);
        }
    }

    @Override
    public void set(ByteList bytes) {
        this.result = bytes;
        clearData();
    }

    public void reset() {
        this.result = data.getBytes();
        clearData();
    }

    @Override
    public String toString() {
        return "Ctx " + "'" + name + '\'';
    }

    public void refresh() {
        for (List<?> list : typedTmp) {
            list.clear();
        }
        getData().writer.clump();
        List<Constant> csts = getData().writer.getConstants();
        for (int i = 0; i < csts.size(); i++) {
            Constant cst = csts.get(i);
            switch (cst.type()) {
                case CstType.INTERFACE:
                case CstType.METHOD:
                    typedTmp[ID_METHOD].add(cst);
                    break;
                case CstType.CLASS:
                    typedTmp[ID_CLASS].add(cst);
                    break;
                case CstType.FIELD:
                    typedTmp[ID_FIELD].add(cst);
                    break;
                case CstType.INVOKE_DYNAMIC:
                    typedTmp[ID_INVOKE_DYN].add(cst);
                    break;
            }
        }
        getName();
        //Util.ThreadBasedCache.remove();
        //initCache();
    }

    public String getName() {
        if(data == null)
            return name;
        String realName = data.nameCst.getValue().getString();
        if(!realName.equals(data.name))
            this.name = new CharList(realName.length() + 6).append(realName).append(".class").toString();
        return this.name;
    }

    public void validateSelf() {
        getData();
        Parser.parse(get(), 0);
        getData();
        get();
    }
}