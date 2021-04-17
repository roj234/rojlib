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
package roj.net.tcp.client;

import roj.config.ParseException;
import roj.math.MathUtils;
import roj.math.Version;
import roj.net.tcp.util.HTTPHeaderLexer;
import roj.net.tcp.util.Headers;
import roj.net.tcp.util.Notify;
import roj.net.tcp.util.Shared;

/**
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 15:30
 */
public class HttpHeader {
    public final int code;
    public final String version;
    public final String codeString;
    public final Headers headers;

    public HttpHeader(String version, int code, String codeString, Headers headers) {
        this.version = version;
        this.code = code;
        this.codeString = codeString;
        this.headers = headers;
    }

    public Version version() {
        return new Version(version);
    }

    @Override
    public String toString() {
        return "HttpHeader{v='" + version + '\'' + ", code=" + code + ' ' + codeString + ", headers=" + headers + '}';
    }

    public static HttpHeader parse(HTTPHeaderLexer lexer, CharSequence action) throws ParseException {
        try {
            String version = lexer.readHttpWord();
            if (version == null || !version.startsWith("HTTP/")) {
                throw lexer.err("Illegal header " + version);
            }

            String code = lexer.readHttpWord();
            int codeInt;
            try {
                codeInt = MathUtils.parseInt(code);
            } catch (NumberFormatException e) {
                throw lexer.err("Illegal code " + code);
            }

            lexer.index++;
            String codeString = lexer.readLine();

            Headers headers = new Headers();
            while (true) {
                String t = lexer.readHttpWord();
                if (t == Shared._ERROR) {
                    throw lexer.err("Unexpected " + t);
                } else if (t == Shared._SHOULD_EOF) {
                    break;
                } else if (t == null) {
                    break;
                } else {
                    headers.add(t, lexer.readHttpWord());
                }
            }

            return new HttpHeader(version.substring(version.indexOf(' ') + 1), codeInt, codeString, headers);
        } catch (Notify notify) {
            String code;
            switch (notify.code) {
                case -127:
                    code = "Received data > Max receive size";
                    break;
                case -128:
                    code = "Timeout";
                    break;
                default:
                    code = "Unknown";
                    break;
            }
            throw lexer.err(code, notify);
        }
    }
}
