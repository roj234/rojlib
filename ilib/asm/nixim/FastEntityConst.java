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
package ilib.asm.nixim;

import ilib.asm.util.MCHooks;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.ToIntMap;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Function;

/**
 * @author Roj234
 * @since  2020/8/21 21:34
 */
@Nixim("net.minecraftforge.fml.common.registry.EntityEntry")
class FastEntityConst {
    @Shadow("factory")
    Function<World, ? extends Entity> factory;
    @Shadow("cls")
    Class<? extends Entity> cls;

    @Copy
    static MCHooks.ICreator entityCreator;
    @Copy
    static ToIntMap<String> entityCreatorId;
    @Copy(staticInitializer = "initEC")
    static RandomAccessFile entityCache;

    static void initEC() {
        try {
            entityCache = new RandomAccessFile("Implib_FEC.bin", "rw");
            ToIntMap<String> map = entityCreatorId = new ToIntMap<>();
            MCHooks.ICreator creator = (MCHooks.ICreator) MCHooks.batchGenerate(entityCache, true, map);
            if (creator != null) {
                entityCreator = creator;
                System.out.println("使用BatchGen节省了 " + map.size() + " 个无用的class");
            }
            entityCache.seek(0);
            entityCache.writeInt(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Inject("init")
    protected void init() {
        if (entityCache != null) {
            try {
                MCHooks.batchAdd(entityCache, cls.getName(), cls);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Object o;
        int i = entityCreatorId.getOrDefault(cls.getName(), -1);
        if (i >= 0) {
            ((MCHooks.ICreator) (o = entityCreator.clone())).setId(i);
        } else {
            o = DirectAccessor.builder(Function.class).construct(cls, "apply", World.class).build();
        }

        factory = Helpers.cast(o);
    }
}
