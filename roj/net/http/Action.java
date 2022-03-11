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

/**
 * @author Roj234
 * @since  2020/11/28 20:17
 */
public final class Action {
    public static final int
            GET = 1,
            POST = 2,
            PUT = 3,
            HEAD = 4,
            DELETE = 5,
            OPTIONS = 6,
            TRACE = 7,
            CONNECT = 8;

    public static int valueOf(String name) {
        if (name == null) return -1;
        switch (name) {
            case "GET":     return GET;
            case "POST":    return POST;
            case "PUT":     return PUT;
            case "HEAD":    return HEAD;
            case "DELETE":  return DELETE;
            case "OPTIONS": return OPTIONS;
            case "TRACE":   return TRACE;
            case "CONNECT": return CONNECT;
            default:        return -1;
        }
    }

    public static String toString(int name) {
        switch (name) {
            case GET:     return "GET";
            case POST:    return "POST";
            case PUT:     return "PUT";
            case HEAD:    return "HEAD";
            case DELETE:  return "DELETE";
            case OPTIONS: return "OPTIONS";
            case TRACE:   return "TRACE";
            case CONNECT: return "CONNECT";
            default:      return null;
        }
    }
}
