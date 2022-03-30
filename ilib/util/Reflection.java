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
package ilib.util;

import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.util.math.Vec3d;
import roj.asm.type.Type;
import roj.reflect.DirectAccessor;

import java.util.EnumSet;
import java.util.List;

/**
 * 以后记得在这里写用到的地方，虽然ide也查得到... <br>
 *     那就不写好了
 *
 * @author Roj233
 * @since 2021/8/25 1:06
 */
public interface Reflection {
    Reflection HELPER = preloadNecessaryClassesBeforeDefine(DirectAccessor.builder(Reflection.class))
            .access(Vec3d.class, new String[] {"field_72450_a", "field_72448_b", "field_72449_c" },
                    null,
                    new String[] { "setVecX", "setVecY", "setVecZ" })
            .delegate(EnumSet.class, "addAll")
            .i_access("java/util/Collections$UnmodifiableList", "list", new Type("java/util/List"), "getModifiableList", null, false)
            .access(BlockRedstoneWire.class, "field_150181_a", null, "setRedstoneProvidePower")
            .build();

    static DirectAccessor<Reflection> preloadNecessaryClassesBeforeDefine(DirectAccessor<Reflection> builder) {
        return builder;
    }

    // region Field
    void setVecX(Vec3d vector, double x);
    void setVecY(Vec3d vector, double y);
    void setVecZ(Vec3d vector, double z);

    <T> List<T> getModifiableList(List<T> unmodifiableList);

    void setRedstoneProvidePower(BlockRedstoneWire block, boolean redstoneProvidePower);
    // endregion
    // region Method
    void addAll(EnumSet<?> set);
    // endregion
    // region Construct
    // endregion

}
