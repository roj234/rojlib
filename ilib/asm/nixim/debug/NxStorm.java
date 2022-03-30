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
package ilib.asm.nixim.debug;

import ilib.client.KeyRegister;
import ilib.client.renderer.ClimateCloudRenderer;
import ilib.world.Climated;
import ilib.world.StormHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;
import roj.math.Vec3d;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since  2022/4/4 19:31
 */
@Nixim(value = "net.minecraft.world.WorldProviderSurface", copyItf = true)
public class NxStorm extends WorldProviderSurface implements Climated {
    @Override
    @Copy
    public StormHandler getStormHandler() {
        if (getCloudRenderer() == null) setCloudRenderer(new ClimateCloudRenderer(MyStormHandler.msp));
        return MyStormHandler.msp;
    }

    public static class MyStormHandler extends StormHandler {
        public static MyStormHandler msp = new MyStormHandler();

        @Override
        public void updateStrength(World world) {
            strength = KeyRegister.stormStrength;
        }

        /**
         * pos为null时返回平均风速
         */
        @Override
        public Vec3d getStormVelocity(World world, @Nullable BlockPos pos, Vec3d dest) {
            return dest.set(KeyRegister.stormDirection);
        }
    }
}
