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
package roj.net.ssl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/6 22:11
 */
public class ServerSslConf implements SslConfig {
    private final String keyStore;
    private final char[] password;

    public ServerSslConf(String keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }

    @Override
    public boolean isServerSide() {
        return true;
    }

    @Override
    public boolean isNeedClientAuth() {
        return false;
    }

    @Override
    public InputStream getPkPath() {
        try {
            return new FileInputStream(keyStore);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public InputStream getCaPath() {
        return getPkPath();
    }

    @Override
    public char[] getPasswd() {
        return password;
    }
}
