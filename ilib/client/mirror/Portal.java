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
package ilib.client.mirror;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import static ilib.ClientProxy.mc;

public abstract class Portal {
    public WorldClient world;

    protected EnumFacing faceOn;
    protected AxisAlignedBB plane;
    protected BlockPos pos;
    protected Portal pair;

    public Portal(WorldClient world) {
        this.world = world;
        this.pos = BlockPos.ORIGIN;

        this.faceOn = EnumFacing.NORTH;
    }

    public Portal(WorldClient world, BlockPos position, EnumFacing faceOn) {
        this.world = world;
        this.pos = position;

        this.faceOn = faceOn;
    }

    public abstract void renderPlane(float partialTick);

    public EnumFacing getFaceOn() {
        return faceOn;
    }

    public BlockPos getPos() {
        return new BlockPos(plane.getCenter());
    }

    public WorldClient getWorld() {
        return world;
    }

    public AxisAlignedBB getPlane() {
        return plane;
    }

    public boolean getCullRender() {
        return true;
    }

    public boolean getOneSideRender() {
        return true;
    }

    public boolean renderNearerFog() {
        return false;
    }

    public boolean hasPair() {
        return pair != null;
    }

    public Portal getPair() {
        return pair;
    }

    public void setPair(Portal portal) {
        if (pair != portal) {
            pair = portal;
        }
    }

    public void setPosition(BlockPos v) {
        this.pos = v.toImmutable();
    }

    public int getRenderDistanceChunks() {
        return Math.min(mc.gameSettings.renderDistanceChunks, Mirror.maxRenderDistanceChunks);
    }
}
