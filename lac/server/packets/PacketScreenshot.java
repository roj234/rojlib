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
package lac.server.packets;

import lac.server.TimeoutHandler;
import roj.util.ByteList;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayServer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Roj233
 * @since 2021/10/15 19:51
 */
public class PacketScreenshot implements Packet<INetHandlerPlayServer> {
    private byte[] image;

    @Override
    public void readPacketData(PacketBuffer buffer) {
        byte[] img = new byte[buffer.readableBytes()];
        buffer.readBytes(img);
        this.image = img;
    }

    @Override
    public void writePacketData(PacketBuffer buffer) {}

    @Override
    public void processPacket(INetHandlerPlayServer handler) {
        int state = 0;
        String name = "shot-" + ((NetHandlerPlayServer) handler).player.getName() + "-" + System.currentTimeMillis() + ".png";
        try(FileOutputStream fos = new FileOutputStream(name)) {
            Inflater inf = new Inflater(true);
            try (InflaterInputStream iis = new InflaterInputStream(new ByteList(image).asInputStream(), inf, 1024)) {
                byte[] tmp = new byte[8192];
                int len = iis.read(tmp, 0, 8192);
                if (len <= 0) return;
                fos.write(tmp, 0, len);
            } finally {
                inf.end();
            }
        } catch (IOException e) {
            e.printStackTrace();
            state = 2;
        }
        TimeoutHandler.onPacket("ScreenShot", ((NetHandlerPlayServer) handler).player.getName(), state);
    }
}
