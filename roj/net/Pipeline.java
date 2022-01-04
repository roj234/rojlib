/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.net;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @since 2022/1/8 18:19
 */
public interface Pipeline {
    /**
     * 解码收到的数据
     * @param rcv 接收缓冲
     * @param dst 目的缓冲
     * @return 0ok，正数代表rcv还需要至少n个字节，负数代表dst还需要n个字节
     */
    int unwrap(ByteBuffer rcv, ByteBuffer dst) throws IOException;

    /**
     * 编码数据用于发送
     * @param src 源缓冲
     * @param snd 发送缓冲
     * @return 非负数ok(写出)，负数代表snd还需要至少n个字节
     */
    int wrap(ByteBuffer src, ByteBuffer snd) throws IOException;
}
