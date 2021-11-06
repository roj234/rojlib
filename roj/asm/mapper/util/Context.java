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
import roj.asm.tree.ConstantData;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Context implements Consumer<Constant> {
    static final int ID_METHOD = 0, ID_FIELD = 1, ID_CLASS = 2, ID_INVOKE_DYN = 3;

    private String name;
    private ConstantData data;
    private Object stream;
    private ByteList result;

    private final ArrayList<Constant>[] cstCache = Helpers.cast(new ArrayList<?>[4]);

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
            } else if(result != null) {
                bytes = this.result;
                this.result = null;
            } else
                throw new IllegalStateException(getFileName() + " 没有数据");
            ConstantData data;
            try {
                data = Parser.parseConstants(bytes);
            } catch (Throwable e) {
                final File file = new File(getFileName().replace('/', '.'));
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bytes.writeToStream(fos);
                } catch (IOException ignored) {}
                throw new IllegalArgumentException(name + " 读取失败", e);
            }
            this.data = data;
            getFileName();
        }
        return this.data;
    }

    private static ByteList read0(Object o) {
        if(o instanceof InputStream) {
            try (InputStream in = (InputStream) o) {
                return new ByteList().readStreamArrayFully(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (o instanceof ByteList) {
            return (ByteList) o;
        } else if (o instanceof byte[]) {
            return new ByteList((byte[]) o);
        }
        throw new ClassCastException(o.getClass().getName());
    }

    public List<CstRef> getMethodConstants() {
        cstInit();
        return Helpers.cast(cstCache[ID_METHOD]);
    }

    public List<CstRef> getFieldConstants() {
        cstInit();
        return Helpers.cast(cstCache[ID_FIELD]);
    }

    public List<CstDynamic> getInvokeDynamic() {
        cstInit();
        return Helpers.cast(cstCache[ID_INVOKE_DYN]);
    }

    public List<CstClass> getClassConstants() {
        cstInit();
        return Helpers.cast(cstCache[ID_CLASS]);
    }

    public ByteList get(boolean shared) {
        if(this.result == null) {
            if(this.data != null) {
                try {
                    data.verify();
                    if (shared) {
                        return Parser.toByteArrayShared(data);
                    } else {
                        this.result = new ByteList(Parser.toByteArray(data));
                    }
                } catch (Throwable e) {
                    throw new IllegalArgumentException(name + " 写入失败", e);
                } finally {
                    clearData();
                }
            } else {
                this.result = read0(stream);
                this.stream = null;
            }
        }
        return this.result;
    }

    public ByteList get() {
        return get(false);
    }

    private void clearData() {
        if(this.data != null) {
            getFileName();
            this.data = null;
            if (cstCache[0] != null)
            for (List<?> list : cstCache) {
                list.clear();
            }
        }
    }

    public void set(ByteList bytes) {
        this.result = bytes;
        clearData();
    }

    @Override
    public String toString() {
        return "Ctx " + "'" + name + '\'';
    }

    private void cstInit() {
        if(cstCache[0] == null) {
            cstCache[0] = new ArrayList<>();
            cstCache[1] = new ArrayList<>();
            cstCache[2] = new ArrayList<>();
            cstCache[3] = new ArrayList<>();
        }
        if (cstCache[0].isEmpty()) {
            ConstantPool cw = getData().cp;
            cw.setAddListener(this);
            List<Constant> csts = cw.getConstants();
            for (int i = 0; i < csts.size(); i++) {
                accept(csts.get(i));
            }
            getFileName();
        }
    }

    @Override
    public void accept(Constant cst) {
        if (cst == null) {
            for (List<?> list : cstCache) {
                list.clear();
            }
            cstInit();
            return;
        }
        switch (cst.type()) {
            case CstType.INTERFACE:
            case CstType.METHOD:
                cstCache[ID_METHOD].add(cst);
                break;
            case CstType.CLASS:
                cstCache[ID_CLASS].add(cst);
                break;
            case CstType.FIELD:
                cstCache[ID_FIELD].add(cst);
                break;
            case CstType.INVOKE_DYNAMIC:
                cstCache[ID_INVOKE_DYN].add(cst);
                break;
        }
    }

    public String getFileName() {
        if(data == null)
            return name;
        String realName = data.nameCst.getValue().getString();
        if(!realName.equals(name))
            this.name = realName.concat(".class");
        return this.name;
    }

    public ByteList getCompressedShared() {
        try {
            return Parser.toByteArrayShared(Parser.parse(get(true)));
        } catch (Throwable t) {
            try (FileOutputStream fos = new FileOutputStream(getFileName().replace('/', '_'))) {
                get().writeToStream(fos);
            } catch (Throwable ignored) {}
            throw t;
        }
    }

    public void compress() {
        result = new ByteList(Parser.toByteArrayShared(Parser.parse(get(true))).toByteArray());
    }
}