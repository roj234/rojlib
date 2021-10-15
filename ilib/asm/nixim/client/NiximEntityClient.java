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
package ilib.asm.nixim.client;

import ilib.util.BlockHelper;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/20 22:42
 */
@Nixim("net.minecraft.entity.Entity")
public abstract class NiximEntityClient extends Entity {
    public NiximEntityClient(World worldIn) {
        super(worldIn);
    }

    @Inject("func_70065_x")
    protected void preparePlayerToSpawn() {
        if (this.world != null) {
            if (this.posY > 0 && this.posY < 256) {
                this.posY = BlockHelper.getSurfaceBlockY(this.world, (int) Math.round(this.posX), (int) Math.round(this.posZ));
                this.setPosition(this.posX, this.posY, this.posZ);
            }
            this.motionX = 0;
            this.motionY = 0;
            this.motionZ = 0;
            this.rotationPitch = 0;
        }
    }

}
