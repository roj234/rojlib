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

package ilib.command;

import ilib.command.sub.AbstractSubCommand;
import io.netty.channel.ChannelHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import roj.net.tcp.serv.HttpServer;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.serv.util.Request;
import roj.net.tcp.serv.util.StaticZipRouter;
import roj.net.tcp.util.Code;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.zip.ZipFile;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 20:48
 */
public final class CommandServerManage extends AbstractSubCommand {
    static Thread serverThread;
    static ArrayList<ChannelHandler> mcHandlers;

    @Nonnull
    @Override
    public String getName() {
        return "httpserver";
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString("Usage: /httpserver <port> [static]"));
        } else {
            if (serverThread != null) {
                if (serverThread.isAlive()) {
                    sender.sendMessage(new TextComponentString("Stopping"));
                    serverThread.interrupt();
                } else {
                    serverThread = null;
                    sender.sendMessage(new TextComponentString("Stopped"));
                    /*if(mcHandlers != null) {
                        ArrayList<ChannelHandler> handlers = mcHandlers;
                        mcHandlers = null;

                        NetworkSystem system = server.getNetworkSystem();
                        ChannelFuture future = system.endpoints.get(0);
                        ChannelPipeline pipeline = future.channel().pipeline();
                        for (int i = handlers.size() - 1; i >= 0; i--) {
                            pipeline.addLast(handlers.get(i));
                        }
                    }*/
                }
            } else {
                try {
                    Router router = args.length > 1 ? new StaticZipRouter(new ZipFile(args[1])) : new ServerManageRouter();
                    int port = Integer.parseInt(args[0]);
                    /*if(port == server.getServerPort()) {

                        final TextComponentString msg = new TextComponentString("[ImpLib]Http server is running on port " + port);
                        SPacketDisconnect packet = new SPacketDisconnect(msg);
                        final GenericFutureListener<?>[] tmp = new GenericFutureListener[0];
                        for(EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                            final NetworkManager manager = player.connection.netManager;
                            manager.sendPacket(packet, future -> manager.closeChannel(msg), Helpers.cast(tmp));
                        }

                        NetworkSystem system = server.getNetworkSystem();
                        ChannelFuture future = system.endpoints.get(0);
                        ChannelPipeline pipeline = future.channel().pipeline();

                        ArrayList<ChannelHandler> handlers = new ArrayList<>(10);
                        ChannelHandler ch;
                        while ((ch = pipeline.removeLast()) != null) {
                            handlers.add(ch);
                        }
                        mcHandlers = handlers;
                        pipeline.addLast("IL_reader", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                ByteBuf buf = (ByteBuf)msg;
                                buf.markReaderIndex();

                                ByteBuf write = buf.alloc().buffer(128);

                                // probably read done...
                                Request request = Request.getRequest();
                                Response reply = router.response(null, request);
                                reply.prepare();
                                while (reply.send(new NettyWrap(write))) {

                                }
                                reply.release();
                                writeAndFlush(ctx, buf);
                            }

                            private void writeAndFlush(ChannelHandlerContext ctx, ByteBuf buf) {
                                ctx.pipeline().firstContext().writeAndFlush(buf);//.addListener(ChannelFutureListener.CLOSE);
                            }
                        });
                    }*/

                    HttpServer server1 = new HttpServer(port, 512, router);
                    Thread thread = new Thread(server1, "ImpLib-HTTPServer");
                    thread.setDaemon(true);
                    thread.start();

                    sender.sendMessage(new TextComponentString("Started Listening on ::0:" + args[0]));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    private static class ServerManageRouter implements Router {
        @Override
        public Response response(Socket socket, Request request) {
            switch (request.path()) {
                case "status":
                case "tps":
                case "mem":
                case "execution":
            }
            return new Reply(Code.NOT_FOUND, new StringResponse("服务器制作中"));
        }
    }
}
