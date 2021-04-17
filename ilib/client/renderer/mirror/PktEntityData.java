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
package ilib.client.renderer.mirror;

import ilib.network.IMessage;
import net.minecraft.entity.Entity;
import roj.util.ByteReader;
import roj.util.ByteWriter;

public class PktEntityData implements IMessage {
    public int id;
    public double lX, lY, lZ;
    public double x, y, z;
    public float prevYaw, prevPitch, yaw, pitch;
    public double mX, mY, mZ;

    public PktEntityData() {
    }

    public PktEntityData(Entity ent) {
        id = ent.getEntityId();
        lX = ent.lastTickPosX;
        lY = ent.lastTickPosY;
        lZ = ent.lastTickPosZ;
        x = ent.posX;
        y = ent.posY;
        z = ent.posZ;
        prevYaw = ent.prevRotationYaw;
        prevPitch = ent.prevRotationPitch;
        yaw = ent.rotationYaw;
        pitch = ent.rotationPitch;
        mX = ent.motionX;
        mY = ent.motionY;
        mZ = ent.motionZ;
    }

    public PktEntityData(int id, double lastX, double lastY, double lastZ, double x, double y, double z, float prevYaw, float prevPitch, float yaw, float pitch, double mX, double mY, double mZ) {
        this.id = id;
        this.lX = lastX;
        this.lY = lastY;
        this.lZ = lastZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.prevYaw = prevYaw;
        this.prevPitch = prevPitch;
        this.yaw = yaw;
        this.pitch = pitch;
        this.mX = mX;
        this.mY = mY;
        this.mZ = mZ;
    }

    @Override
    public void fromBytes(ByteReader buf) {
        id = buf.readVarInt(false);
        lX = buf.readDouble();
        lY = buf.readDouble();
        lZ = buf.readDouble();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        prevYaw = buf.readFloat();
        prevPitch = buf.readFloat();
        yaw = buf.readFloat();
        pitch = buf.readFloat();
        mX = buf.readDouble();
        mY = buf.readDouble();
        mZ = buf.readDouble();
    }

    @Override
    public void toBytes(ByteWriter buf) {
        buf.writeVarInt(id, false);
        buf.writeDouble(lX);
        buf.writeDouble(lY);
        buf.writeDouble(lZ);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(prevYaw);
        buf.writeFloat(prevPitch);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeDouble(mX);
        buf.writeDouble(mY);
        buf.writeDouble(mZ);
    }
}
