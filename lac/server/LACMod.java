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
package lac.server;

import ilib.ATHandler;
import ilib.asm.ClassReplacer;
import ilib.asm.NiximProxy;
import ilib.command.MasterCommand;
import ilib.util.PlayerUtil;
import lac.server.cmd.CmdLAC;
import lac.server.cmd.LoginCmd;
import lac.server.packets.PacketClassCheck;
import lac.server.packets.PacketLogin;
import lac.server.packets.PacketScreenshot;
import lac.server.packets.SPacketCrash;
import roj.io.IOUtil;
import roj.util.Helpers;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * LAC Mod Server-side entry
 * @author Roj234
 * @since 2021/7/8 18:53
 */
@Mod(modid = LACMod.MODID, name = LACMod.NAME, version = LACMod.VERSION, dependencies = "required:forge@[14.23.4.2768,); required-after:ilib@[0.4.0,);", serverSideOnly = true)
@Mod.EventBusSubscriber
public class LACMod {
    public static final String MODID = "lac";
    public static final String NAME = "LaoAntiCheat";
    public static final String VERSION = "0.1.0";

    static {
        try {
            NiximProxy.Nx(IOUtil.read(LACMod.class, "lac/server/nixim/NxC00Handshake.class"));
            if (Config.ENCRYPT_IV.length > 0)
                NiximProxy.Nx(IOUtil.read(LACMod.class, "lac/server/nixim/NxEncryption.class"));
            NiximProxy.Nx(IOUtil.read(LACMod.class, "lac/server/nixim/NxNetworkMgr.class"));
            ClassReplacer.add(
                    "net.minecraftforge.fml.common.network.handshake.FMLHandshakeServerState",
                    IOUtil.read(LACMod.class, "lac/server/nixim/NxFMLHS_S.class"));
            for (int i = 1; i <= 7; i++) {
                ClassReplacer.add(
                        "net.minecraftforge.fml.common.network.handshake.FMLHandshakeServerState$" + i,
                        IOUtil.read(LACMod.class, "lac/server/nixim/NxFMLHS_S$" + i + ".class"),
                        "net.minecraftforge.fml.common.network.handshake.FMLHandshakeServerState");
            }
        } catch (IOException e) {
            Helpers.athrow(e);
        }
    }

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent e) {
        // todo: solid ID
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, PacketScreenshot.class);
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, PacketClassCheck.class);
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, SPacketCrash.class);
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, PacketLogin.class);

        MinecraftForge.EVENT_BUS.register(LACMod.class);
    }

    @Mod.EventHandler
    public void onStartServer(FMLServerStartingEvent e) {
        if(Config.enableLoginModule) {
            LoginMgr.load();
            e.registerServerCommand(new MasterCommand("login", 1).aliases("l")
                    .register(new LoginCmd())
                    .register(LoginCmd.CHANGE_PASS)
                    .register(LoginCmd.SAVE_LOAD));
        }
        e.registerServerCommand(new CmdLAC());
        checkTicker = oneSecond = 0;
    }

    static int oneSecond, checkTicker;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++oneSecond == 20) {
            LoginMgr.update();
            TimeoutHandler.update();
            oneSecond = 0;
        }
        if (checkTicker-- == 0) {
            long ntr = System.nanoTime();
            checkTicker = 100 + Math.abs((int) ntr % 2580);

            List<EntityPlayerMP> emp = PlayerUtil.getMinecraftServer().getPlayerList().getPlayers();
            EntityPlayerMP player = emp.get((int) (ntr % emp.size()));
            checkPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP)
            checkPlayer((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerLoggedOutEvent event) {
        TimeoutHandler.clean(event.player.getName());
    }

    private static void checkPlayer(EntityPlayerMP player) {
        System.out.println("Check " + player.getName());

        MinecraftServer server = PlayerUtil.getMinecraftServer();
        Random rand = server.worlds[0].rand;
        rand.setSeed(server.tickTimeArray[0] + server.tickTimeArray[99]);

        NetworkManager man = player.connection.netManager;

        int[] data = ((CacheBridge) man).getClassArray(true);
        for (int i = 0; i < data.length; i++) {
            data[i] = rand.nextInt(ModInfo.classListOrdered.length);
        }
        man.sendPacket(new PacketClassCheck(data));
    }
}
