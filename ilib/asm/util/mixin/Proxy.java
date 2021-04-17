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
package ilib.asm.util.mixin;

import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.service.ILegacyClassTransformer;

import java.util.Collections;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/30 15:19
 */
public final class Proxy implements IClassTransformer, ILegacyClassTransformer {
    private static List<Proxy> proxies = Collections.emptyList();
    private static Proxy selfProxyLast;
    private static NVE_1 transformer = new NVE_1();
    private boolean isActive = true;

    public Proxy() {
        if (selfProxyLast != null) {
            selfProxyLast.isActive = false;
        }
        selfProxyLast = this;

        //Logger.getLogger("mixin").debug("Adding new mixin transformer proxy #{}", new Object[]{proxies.size()});
    }

    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        return transformClassBytes(name, transformedName, basicClass);
    }

    public String getName() {
        return this.getClass().getName();
    }

    public boolean isDelegationExcluded() {
        return true;
    }

    public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
        try {
            return this.isActive ? transformer.transformClassBytes(name, transformedName, basicClass) : basicClass;
        } catch (Throwable e) {
            e.printStackTrace();
            return basicClass;
        }
    }
}
