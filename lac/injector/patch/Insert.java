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
package lac.injector.patch;

import lac.client.packets.PacketClassCheck;
import lac.client.packets.PacketLogin;
import lac.client.packets.PacketScreenshot;
import lac.client.packets.SPacketCrash;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;

/**
 * LAC Mod Client-side entry
 * @author Roj234
 * @since 2021/7/9 2:02
 */
class Insert {
    public static void _ENTRY() {
        _BOTH(EnumConnectionState.PLAY, PacketScreenshot.class);
        _BOTH(EnumConnectionState.PLAY, PacketClassCheck.class);
        _BOTH(EnumConnectionState.PLAY, PacketLogin.class);
        _RECEIVE(SPacketCrash.class);
    }

    public static void _BOTH(EnumConnectionState state, Class<? extends Packet<?>> clz) {
        state.registerPacket(EnumPacketDirection.CLIENTBOUND, clz);
        state.registerPacket(EnumPacketDirection.SERVERBOUND, clz);
        EnumConnectionState.STATES_BY_CLASS.put(clz, state);
    }

    public static void _RECEIVE(Class<? extends Packet<?>> clz) {
        EnumConnectionState.PLAY.registerPacket(EnumPacketDirection.CLIENTBOUND, clz);
        EnumConnectionState.STATES_BY_CLASS.put(clz, EnumConnectionState.PLAY);
    }
}
