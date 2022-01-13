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
package roj.net.http;

import roj.config.ParseException;
import roj.math.Version;
import roj.net.Notify;

/**
 * @author Roj234
 * @since  2020/12/5 15:30
 */
public class HttpHead {
    public final String[] abc;
    public final boolean isRequest;
    public final Headers headers;

    public HttpHead(Headers headers, boolean request, String... abc) {
        assert abc.length == 3;
        this.abc = abc;
        this.isRequest = request;
        this.headers = headers;
    }

    public int getCode() {
        if (isRequest) throw new IllegalStateException();
        return Integer.parseInt(abc[1]);
    }

    public String getCodeString() {
        if (isRequest) throw new IllegalStateException();
        return abc[2];
    }

    public String getPath() {
        if (!isRequest) throw new IllegalStateException();
        return abc[1];
    }

    public String getMethod() {
        if (!isRequest) throw new IllegalStateException();
        return abc[0];
    }

    public String versionStr() {
        return isRequest ? abc[2] : abc[0];
    }

    public Version version() {
        return new Version(isRequest ? abc[2] : abc[0]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder()
                .append(abc[0]).append(' ').append(abc[1]).append(' ').append(abc[2])
                .append("\r\n");
        headers.encode(sb);
        return sb.append("\r\n").toString();
    }

    public static HttpHead parse(HttpLexer lexer) throws ParseException {
        try {
            String version_method = lexer.readHttpWord();

            String code_url = lexer.readHttpWord();
            lexer.index++;
            String codeDesc_version = lexer.readLine();

            boolean request = false;
            if (version_method == null || !version_method.startsWith("HTTP/")) {
                if (!codeDesc_version.startsWith("HTTP/")) throw lexer.err("Illegal header " + version_method);
                request = true;
            }

            return new HttpHead(parseHeadFields(lexer), request, version_method.substring(version_method.indexOf(' ') + 1), code_url, codeDesc_version);
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

    public static Headers parseHeadFields(HttpLexer lexer) throws ParseException {
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
        return headers;
    }
}
