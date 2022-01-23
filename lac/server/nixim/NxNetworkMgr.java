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
package lac.server.nixim;

import lac.server.CacheBridge;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;

import java.util.Map;

/**
 * Use NetworkManager as CacheBridge
 *
 * @author Roj233
 * @since 2021/10/15 21:16
 */
@Nixim(value = "net.minecraft.network.NetworkManager", copyItf = true)
abstract class NxNetworkMgr implements CacheBridge {
    @Copy
    private Map<String, String> modList;
    @Copy
    private int[]               classArray;

    @Copy
    public final int[] getClassArray(boolean reset) {
        if (reset) {
            return classArray = new int[(int) (System.nanoTime() & 127) + 1];
        } else {
            int[] tmp = classArray;
            classArray = null;
            return tmp;
        }
    }

    @Copy
    @Override
    public final Map<String, String> getModList() {
        return modList;
    }

    @Copy
    @Override
    public final void setModList(Map<String, String> map) {
        this.modList = map;
    }
}
