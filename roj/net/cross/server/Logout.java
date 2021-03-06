/*
 * This file is a part of MoreItems
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
package roj.net.cross.server;

import java.io.IOException;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:43
 */
final class Logout extends Stated {
    static final Logout LOGOUT = new Logout();
    static final Logout REQUESTED = new Logout();

    @Override
    void enter(Client self) {
        self.timer = 50;
        self.st1 = 0;
    }

    @Override
    Stated next(Client self) {
        if (self.st1 == 0) {
            self.ch.buffer().clear();
            try {
                write1(self.ch, (byte) P_LOGOUT);
            } catch (IOException ignored) {}
            self.st1 = 1;
        }
        if (this == LOGOUT) {
            try {
                if (self.ch.read() == 0 && self.timer-- > 0) {
                    return this;
                }
            } catch (IOException ignored) {}
        }
        syncPrint(self + ": ??????");
        return null;
    }
}
