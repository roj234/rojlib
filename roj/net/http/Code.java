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

import roj.collect.CharMap;

/**
 * A helper class which define the HTTP response codes
 */
public class Code {
    public static final int
    SWITCHING_PROTOCOL = 101,

    OK =  200,

    MOVED_PERMANENTLY = 301,
    FOUND = 302,
    NOT_MODIFIED = 304,

    BAD_REQUEST = 400,
    FORBIDDEN =   403,
    NOT_FOUND =   404,
    METHOD_NOT_ALLOWED = 405,
    TIMEOUT = 408,
    ENTITY_TOO_LARGE = 413,
    URI_TOO_LONG = 414,
    UPGRADE_REQUIRED = 426,

    INTERNAL_ERROR = 500,
    UNAVAILABLE = 503;

    public static String getDescription(int code) {
        return codes.get((char) code);
    }

    private static final CharMap<String> codes = new CharMap<>();

    static {
        codes.put((char) 100, "Continue");
        codes.put((char) 101, "Switching Protocols");
        codes.put((char) 200, "OK");
        codes.put((char) 201, "Created");
        codes.put((char) 202, "Accepted");
        codes.put((char) 203, "Non-Authoritative Information");
        codes.put((char) 204, "No Content");
        codes.put((char) 205, "Reset Content");
        codes.put((char) 206, "Partial Content");
        codes.put((char) 300, "Multiple Choices");
        codes.put((char) 301, "Moved Permanently");
        codes.put((char) 302, "Found");
        codes.put((char) 303, "See Other");
        codes.put((char) 304, "Not Modified");
        codes.put((char) 305, "Use Proxy");
        codes.put((char) 306, "(Unused)");
        codes.put((char) 307, "Temporary Redirect");
        codes.put((char) 400, "Bad Request");
        codes.put((char) 401, "Unauthorized");
        codes.put((char) 402, "Payment Required");
        codes.put((char) 403, "Forbidden");
        codes.put((char) 404, "Not Found");
        codes.put((char) 405, "Method Not Allowed");
        codes.put((char) 406, "Not Acceptable");
        codes.put((char) 407, "Proxy Authentication Required");
        codes.put((char) 408, "Request Timeout");
        codes.put((char) 409, "Conflict");
        codes.put((char) 410, "Gone");
        codes.put((char) 411, "Length Required");
        codes.put((char) 412, "Precondition Failed");
        codes.put((char) 413, "Request Entity Too Large");
        codes.put((char) 414, "Request-URI Too Long");
        codes.put((char) 415, "Unsupported Media Type");
        codes.put((char) 416, "Requested Range Not Satisfiable");
        codes.put((char) 417, "Expectation Failed");
        codes.put((char) 500, "Internal Server Error");
        codes.put((char) 501, "Not Implemented");
        codes.put((char) 502, "Bad Gateway");
        codes.put((char) 503, "Service Unavailable");
        codes.put((char) 504, "Gateway Timeout");
        codes.put((char) 505, "HTTP Version Not Supported");
    }
}
