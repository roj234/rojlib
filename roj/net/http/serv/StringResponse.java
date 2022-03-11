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
package roj.net.http.serv;

import roj.io.IOUtil;
import roj.net.http.Code;
import roj.net.http.IllegalRequestException;
import roj.util.ByteList;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;

public class StringResponse implements Response {
    final String mime;
    final CharSequence content;

    public StringResponse(CharSequence c, String mime) {
        content = c;
        this.mime = mime + "; charset=UTF-8";
    }

    public StringResponse(CharSequence c) {
        this(c, "text/plain");
    }

    public static StringResponse httpErr(int code) {
        String desc = code + " " + Code.getDescription(code);
        return new StringResponse("<title>" + desc + "</title><center><h1>" + desc + "</h1><hr/><div>Async 2.0.1</div></center>", "text/html");
    }

    public static StringResponse forError(int code, Object e) {
        if(code == 0) {
            code = e instanceof IllegalRequestException ? ((IllegalRequestException) e).code : Code.INTERNAL_ERROR;
        }

        StringWriter sw = new StringWriter();
        sw.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>");
        sw.write(Code.getDescription(code));
        sw.write("</title></head><body><div><i><h2>出现了错误:</h2></i><p><h3>");
        sw.write(Code.getDescription(code));
        sw.write("</h3><h3><font color='red'>");

        if (e != null) {
            if(e instanceof Throwable) {
                sw.write("以下为错误详细信息: <br/><pre>");
                ((Throwable)e).printStackTrace(new PrintWriter(sw));
                sw.write("</pre>");
            } else {
                sw.write(e.toString());
            }
        }

        sw.write("</font></h3></p><p>您可以点击<a href='javascript:location.reload();'>重试</a>.</p><br/><hr/>" +
                "<div>AsyncHttp 2.0.1</div></div><!-- padding for ie --><!-- padding for ie -->" +
                "<!-- padding for ie --><!-- padding for ie --></body></html>");

        return new StringResponse(sw.toString(), "text/html");
    }

    private ByteBuffer buf;

    public void prepare() {
        if (buf == null) {
            buf = ByteBuffer.wrap(IOUtil.SharedCoder.get().encode(content));
        } else {
            buf.position(0);
        }
    }

    public boolean send(RequestHandler rh) throws IOException {
        if (buf == null) throw new IllegalStateException("Not prepared");
        rh.write(buf);
        return buf.hasRemaining();
    }

    @Override
    public boolean wantCompress() {
        return content.length() > 100;
    }

    @Override
    public void writeHeader(ByteList list) {
        list.putAscii("Content-Type: ").putAscii(mime).putAscii(CRLF)
            .putAscii("Content-Length: ").putAscii(Integer.toString(buf.capacity())).putAscii(CRLF);
    }
}
