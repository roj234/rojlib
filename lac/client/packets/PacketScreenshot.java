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
package lac.client.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ScreenShotHelper;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Roj233
 * @since 2021/10/15 19:51
 */
public class PacketScreenshot implements Packet<INetHandler> {
    private static class PacketBufferOutputStream extends OutputStream {
        private final PacketBuffer buffer;

        public PacketBufferOutputStream(PacketBuffer buffer) {this.buffer = buffer;}

        @Override
        public void write(int b) {
            buffer.writeByte(b);
        }

        @Override
        public void write(@Nonnull byte[] b, int off, int len) {
            buffer.writeBytes(b, off, len);
        }
    }

    private BufferedImage img;

    @Override
    public void readPacketData(PacketBuffer buffer) {}

    @Override
    public void writePacketData(PacketBuffer buffer) throws IOException {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        DeflaterOutputStream dos = new DeflaterOutputStream(new PacketBufferOutputStream(buffer), def, 1024, false);
        ImageIO.write(this.img, "png", dos);
        dos.finish();
        def.end();
    }

    @Override
    public void processPacket(INetHandler handler) {
        Minecraft mc = Minecraft.getMinecraft();
        img = ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        mc.player.connection.sendPacket(this);
    }
}
