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
package ilib.client.util.model;

import ilib.ImpLib;
import roj.io.BoxFile;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.File;
import java.io.IOException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/2 2:19
 */
public final class BlockStateBuilderCached extends BlockStateBuilder {
    static File    cacheFl = new File(".cache/");
    static BoxFile cache;

    private CharSequence storedData;
    private final String cacheId;

    private BlockStateBuilderCached(boolean isItem, String file) {
        super(isItem);
        this.cacheId = file;
    }

    private BlockStateBuilderCached(CharSequence cached) {
        super("{}", false);
        this.storedData = cached;
        this.cacheId = null;
    }

    public BlockStateBuilderCached(String json, boolean isItem, String cacheFile) {
        super(json, isItem);
        this.cacheId = cacheFile;
    }

    public boolean isCached() {
        return storedData != null;
    }

    static {
        if (!cacheFl.isDirectory() && !cacheFl.mkdirs()) {
            throw new IllegalStateException("Unable to create cache folder " + cache);
        }
    }

    @Override
    public String build() {
        if (storedData != null)
            return storedData.toString();
        String val = super.build();
        try {
            cache.append(cacheId, ByteWriter.encodeUTF(val));
        } catch (IOException e) {
            ImpLib.logger().error(e);
        }
        return val;
    }

    public static BlockStateBuilderCached from(String cacheId, String json, boolean isItem) {
        if(cache == null) {
            File cacheFile = new File(cacheFl, "bsb.tmp");
            try {
                cache = new BoxFile(cacheFile);
                cache.load();
                String v = cache.getUTF("version");
                if(!ImpLib.MODEL_HASH.equals(v)) {
                    if(v != null)
                        cache.clear();
                    cache.append("version", ByteWriter.encodeUTF(ImpLib.MODEL_HASH));
                }
            } catch (IOException e) {
                ImpLib.logger().error(e);
            }
        }

        try {
            ByteList data = cache.get(cacheId, new ByteList());
            if(data != null && data.pos() > 0) {
                CharList out = new CharList();
                ByteReader.decodeUTF(-1, out, data);
                return new BlockStateBuilderCached(out);
            } else {
                return new BlockStateBuilderCached(json, isItem, cacheId);
            }
        } catch (IOException e) {
            ImpLib.logger().error(e);
            return new BlockStateBuilderCached(json, isItem, cacheId);
        }
    }

    public static BlockStateBuilderCached from(String cacheId, boolean isItem) {
        return from(cacheId, "{}", isItem);
    }
}
