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
package ilib.asm.nixim.client.bug;

import ilib.asm.util.MyCnInit;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.LazyLoadBase;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.network.NetworkManager")
abstract class GroupNPE extends NetworkManager {
    public GroupNPE(EnumPacketDirection p_i46004_1_) {
        super(p_i46004_1_);
    }

    @Inject("func_181124_a")
    public static NetworkManager createNetworkManagerAndConnect(InetAddress address, int port, boolean useEPollIO) {
        if (address instanceof Inet6Address) {
            System.setProperty("java.net.preferIPv4Stack", "false");
        }

        final NetworkManager man = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        Class<? extends Channel> channelClass;
        LazyLoadBase<? extends EventLoopGroup> loader;
        if (Epoll.isAvailable() && useEPollIO) {
            channelClass = EpollSocketChannel.class;
            loader = CLIENT_EPOLL_EVENTLOOP;
        } else {
            channelClass = NioSocketChannel.class;
            loader = CLIENT_NIO_EVENTLOOP;
        }

        EventLoopGroup group;

        while (true) {
            if ((group = loader.getValue()) != null)
                break;
        }

        (new Bootstrap()).group(group).handler(new MyCnInit(man)).channel(channelClass).connect(address, port).syncUninterruptibly();
        return man;
    }

}
