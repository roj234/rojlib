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

import ilib.asm.transformers.NiximProxy;
import ilib.command.MasterCommand;
import lac.common.pkt.PktLogin;
import lac.server.util.EncodeUtil;
import roj.io.IOUtil;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;

/**
 * LAC Mod Server-side entry
 * @author Roj234
 * @since 2021/7/8 18:53
 */
//@Mod(modid = LACMod.MODID, name = LACMod.NAME, version = LACMod.VERSION, dependencies = "required:forge@[14.23.4.2768,); required-after:ilib@[0.4.0,);")
@Mod.EventBusSubscriber
public class LACMod {
    public static final String MODID = "lac";
    public static final String NAME = "LaoAntiCheat";
    public static final String VERSION = "0.1.0-beta";

    public LACMod() throws IOException {
        PktLogin.register();
        MinecraftForge.EVENT_BUS.register(LACMod.class);
        EncodeUtil.initBase64Chars();
        NiximProxy.read(IOUtil.read(LACMod.class, "lac/server/util/ModListServer.class"));
    }

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent e) {

    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent e) {

    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent e) {
        if(Config.enableLoginModule) {
            LoginMgr.load();
        }
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

    }

    static int t;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (t++ >= 20) {
            LoginMgr.update();
            t = 0;
        }
    }
}
