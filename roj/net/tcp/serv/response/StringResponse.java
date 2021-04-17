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
package roj.net.tcp.serv.response;

import roj.net.tcp.util.Code;
import roj.net.tcp.util.IllegalRequestException;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class StringResponse implements HTTPResponse {
    final String mime;
    final CharSequence content;

    public StringResponse(CharSequence c, String mime) {
        content = c;
        this.mime = mime + "; charset=UTF-8";
    }

    public StringResponse(CharSequence c) {
        this(c, "text/plain");
    }

    public static StringResponse errorResponse(Code code, Object e) {
        if(code == null) {
            code = e instanceof IllegalRequestException ? ((IllegalRequestException) e).code : Code.INTERNAL_ERROR;
        }

        StringWriter sw = new StringWriter();
        sw.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>");
        sw.write(code.toString());
        sw.write("</title></head><body><div><i><h2>出现了错误:</h2></i><p><h3>");
        sw.write(code.toString());
        sw.write("</h3><h3><font color='red'>");

        String desc = code.description();
        if (desc == null) {
            if (e != null) {
                if(e instanceof Throwable) {
                    sw.write("以下为错误详细信息: <br/><pre>");
                    ((Throwable)e).printStackTrace(new PrintWriter(sw));
                    sw.write("</pre>");
                } else {
                    sw.write(e.toString());
                }
            }
        } else
            sw.write(desc);

        sw.write("</font></h3></p><p>您可以点击<a href='javascript:location.reload();'>重试</a>.</p><br/><hr/>" +
                "<div>Asyncorized_MC's HTTP(S) Server 1.2.0</div></div><!-- padding for ie --><!-- padding for ie -->" +
                "<!-- padding for ie --><!-- padding for ie --></body></html>");

        return new StringResponse(sw.toString(), "text/html");
    }

    ByteList buf;

    public void prepare() throws IOException {
        if (buf == null) {
            ByteList list = new ByteList(content.length());
            ByteWriter.writeUTF(list, content, (byte) -1);
            list.add((byte) '\r');
            list.add((byte) '\n'); // EOF flag
            buf = list;
        } else {
            buf.trimToSize();
            buf.rewrite();
        }
    }

    public boolean send(WrappedSocket channel) throws IOException {
        if (buf == null)
            throw new IllegalStateException("Not prepared");
        channel.write(buf);

        return buf.remaining() > 0;
    }

    public void release() {
    }

    @Override
    public void writeHeader(CharList list) {
        list.append("Content-Type: ").append(mime).append(CRLF)
                .append("Content-Length: ").append(Integer.toString(buf.remaining() - 2)).append(CRLF);
    }
}
