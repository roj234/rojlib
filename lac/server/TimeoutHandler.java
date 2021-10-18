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
package lac.server;

import ilib.util.PlayerUtil;
import roj.collect.ToIntMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChunkData;

import java.util.Iterator;

/**
 * Timeout handler
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/10/16 12:56
 */
public class TimeoutHandler {
    private static final ToIntMap<String> timer = new ToIntMap<>();

    public static void onPacket(String id, String player, int state) {
        timer.remove(player + "|" + id);
    }

    public static void register(String id, String player, int timeout) {
        timer.putInt(player + "|" + id, timeout);
    }

    public static void clean(String name) {
        for (Iterator<ToIntMap.Entry<String>> itr = timer.selfEntrySet().iterator(); itr.hasNext(); ) {
            ToIntMap.Entry<String> entry = itr.next();
            if (entry.k.startsWith(name) && entry.k.charAt(name.length()) == '|')
                itr.remove();
        }
    }

    public static void update() {
        for (Iterator<ToIntMap.Entry<String>> itr = timer.selfEntrySet().iterator(); itr.hasNext(); ) {
            ToIntMap.Entry<String> entry = itr.next();
            if (--entry.v == 0) {
                int i = entry.k.indexOf('|');
                EntityPlayerMP player = PlayerUtil.getMinecraftServer().getPlayerList().getPlayerByUsername(entry.k.substring(0, i));
                if (player != null) {
                    onTimeout(player, entry.k.substring(i + 1));
                }
                itr.remove();
            }
        }
    }

    private static void onTimeout(EntityPlayerMP player, String id) {
        NetworkManager man = player.connection.netManager;
        man.disableAutoRead();
        player.server.addScheduledTask(() -> man.sendPacket(new SPacketChunkData() {
            @Override
            public void writePacketData(PacketBuffer buf) {
                buf.writeInt(0).writeInt(0).writeBoolean(false);
                buf.writeVarInt(17).writeVarInt(Integer.MAX_VALUE);
                player.server.addScheduledTask(() -> {
                    man.channel().close();
                    man.handleDisconnection();
                });
            }
        }));
    }
}
