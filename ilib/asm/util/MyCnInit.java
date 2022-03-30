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
package ilib.asm.util;

import ilib.Config;
import ilib.net.mock.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NettyPacketDecoder;
import net.minecraft.network.NettyPacketEncoder;
import net.minecraft.network.NetworkManager;

/**
 * @author Roj234
 * @since  2020/9/6 1:46
 */
public class MyCnInit extends ChannelInitializer<Channel> {
    private final NetworkManager man;

    public MyCnInit(NetworkManager man) {
        this.man = man;
    }

    protected void initChannel(Channel channel) {
        try {
            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
        } catch (ChannelException ignored) {}

        PacketAdapter pa = MockingUtil.getPacketAdapter();
        if (pa != null) {
            channel.pipeline()
                   .addLast("timeout", new ReadTimeoutHandler(Config.clientNetworkTimeout))
                   .addLast("splitter", new VarIntDecoder())
                   .addLast("decoder", new ILInboundMocker(pa))
                   .addLast("real_decoder", new NettyPacketDecoder(EnumPacketDirection.CLIENTBOUND))
                   .addLast("prepender", new VarIntEncoder())
                   .addLast("encoder", new ILOutboundMocker(pa))
                   .addLast("real_encoder", new NettyPacketEncoder(EnumPacketDirection.SERVERBOUND))
                   .addLast("packet_handler", new ILStateSniffer(pa))
                   .addLast("real_packet_handler", man);
        } else {
            channel.pipeline()
                   .addLast("timeout", new ReadTimeoutHandler(Config.clientNetworkTimeout))
                   .addLast("splitter", new VarIntDecoder())
                   .addLast("decoder", new NettyPacketDecoder(EnumPacketDirection.CLIENTBOUND))
                   .addLast("prepender", new VarIntEncoder())
                   .addLast("encoder", new NettyPacketEncoder(EnumPacketDirection.SERVERBOUND))
                   .addLast("packet_handler", man);
        }
    }
}
