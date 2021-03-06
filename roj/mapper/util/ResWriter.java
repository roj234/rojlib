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
package roj.mapper.util;

import roj.io.IOUtil;
import roj.io.ZipFileWriter;
import roj.util.ByteList;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
public class ResWriter implements Runnable, Callable<Void> {
    public ResWriter(ZipFileWriter zfw, Map<String, ?> resources) {
        this.zfw = zfw;
        this.resources = resources;
    }

    private final ZipFileWriter  zfw;
    private final Map<String, ?> resources;

    /**
     * Write resource into zip
     */
    @Override
    public void run() {
        try {
            call();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Void call() throws IOException {
        ByteList bl = IOUtil.getSharedByteBuf();
        for (Map.Entry<String, ?> entry : resources.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof InputStream) {
                bl.readStreamFully((InputStream) v);
                v = bl;
            } else if (v.getClass() == String.class) {
                bl.readStreamFully(new FileInputStream(v.toString()));
                v = bl;
            }
            zfw.writeNamed(entry.getKey(), v instanceof ByteList ? (ByteList) v : bl.setArray((byte[]) v));
            bl.clear();
        }
        return null;
    }
}
