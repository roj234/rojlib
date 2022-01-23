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

package ilib.api.tile;

import ilib.api.registry.Indexable;
import org.apache.commons.lang3.NotImplementedException;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/**
 * @author Roj234
 * @since 2021/6/2 23:45
 */
public interface ToolTarget {
    int NO_OP = -1,
            TOGGLE = 1,
            TURN = 2,
            DESTROY = 3,
            SPECIAL = 4;

    int ID_WRENCH = 0;

    class Type extends Indexable.Impl {
        public static final Type WRENCH = new Type("wrench", 100);
        public static final Type CUTTER = new Type("cutter", 300);

        public final int MAX_DAMAGE;

        private static int index;

        public Type(String name, int max_damage) {
            super(name, index++);
            this.MAX_DAMAGE = max_damage;
        }
    }


    int canUse(int tool_id, boolean isSneaking);

    // new: toggleByTool(int type, Object param);
    default void destroyByTool(int tool_id) {
        throw new NotImplementedException("Not implemented yet!");
    }

    default void toggleByTool(int tool_id, EnumFacing face) {
        throw new NotImplementedException("Not implemented yet!");
    }

    NBTTagCompound storeDestroyData();
}