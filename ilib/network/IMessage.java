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

package ilib.network;

import io.netty.buffer.ByteBuf;
import roj.io.IOUtil;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface IMessage extends net.minecraftforge.fml.common.network.simpleimpl.IMessage {
    @Override
    @Deprecated
    default void fromBytes(ByteBuf buf) {
        ByteList tmp = IOUtil.getSharedByteBuf();
        tmp.clear();
        tmp.ensureCapacity(buf.readableBytes());
        tmp.wIndex(buf.readableBytes());
        buf.readBytes(tmp.list, 0, buf.readableBytes());
        fromBytes(tmp);
    }

    @Override
    @Deprecated
    default void toBytes(ByteBuf buf) {
        ByteList tmp = IOUtil.getSharedByteBuf();
        tmp.clear();
        toBytes(tmp);
        buf.writeBytes(tmp.list, 0, tmp.wIndex());
        tmp.clear();
    }

    void fromBytes(ByteList buf);

    void toBytes(ByteList buf);
}