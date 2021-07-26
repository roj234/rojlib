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

import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 16:43
 */
public class ResWriter implements Runnable, Callable<Void> {
    public ResWriter(ZipOutputStream zos, Map<String, ?> resources) {
        this.zos = zos;
        this.resources = resources;
    }

    private final ZipOutputStream zos;
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
        for (Map.Entry<String, ?> entry : resources.entrySet()) {
            ZipEntry ze = new ZipEntry(entry.getKey().replace('\\', '/'));
            zos.putNextEntry(ze);
            Object obj = entry.getValue();
            if(obj instanceof InputStream) {
                try (InputStream is = (InputStream) entry.getValue()) {
                    zos.write(IOUtil.readFully(is));
                }
            } else if(obj instanceof byte[]) {
                zos.write((byte[]) obj);
            } else if(obj instanceof ByteList) {
                ((ByteList) obj).writeToStream(zos);
            } else {
                throw new ClassCastException(obj.getClass().getName());
            }
            zos.closeEntry();
        }
        return null;
    }
}
