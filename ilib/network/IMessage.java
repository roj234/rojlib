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
import roj.util.ByteReader;
import roj.util.ByteWriter;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public interface IMessage extends net.minecraftforge.fml.common.network.simpleimpl.IMessage {
    @Override
    @Deprecated
    default void fromBytes(ByteBuf byteBuf) {
        byte[] arr = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(arr);
        fromBytes(new ByteReader(arr));
    }

    @Override
    @Deprecated
    default void toBytes(ByteBuf byteBuf) {
        ByteWriter w = new ByteWriter();
        toBytes(w);
        byteBuf.writeBytes(w.list.list, w.list.pos(), w.list.limit());
    }

    void fromBytes(ByteReader buf);

    void toBytes(ByteWriter buf);
}