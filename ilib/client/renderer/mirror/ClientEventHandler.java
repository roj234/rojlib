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

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.client.api.ClientChangeWorldEvent;
import ilib.client.renderer.mirror.render.PortalRenderer;
import ilib.client.renderer.mirror.render.world.RenderGlobalProxy;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.PlayerSPPushOutOfBlocksEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import roj.math.MathUtils;

public class ClientEventHandler {
    public static float prevCameraRoll;
    public static float cameraRoll;

    public static RenderGlobalProxy proxy;

    @SubscribeEvent
    public static void onCameraSetupEvent(EntityViewRenderEvent.CameraSetup event) {
        if (cameraRoll != 0F && PortalRenderer.renderLevel <= 0) {
            event.setRoll(MathUtils.interpolate(prevCameraRoll, cameraRoll, (float) event.getRenderPartialTicks()));
        }
    }

    @SubscribeEvent
    public static void onRenderBlockOverlay(RenderBlockOverlayEvent event) {
        if (EventHandler.isInPortal(event.getPlayer()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPushPlayerSPOutOfBlock(PlayerSPPushOutOfBlocksEvent event) {
        if (EventHandler.isInPortal(event.getEntityPlayer()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChangeWorld(ClientChangeWorldEvent event) {
        if (proxy == null) {
            proxy = new RenderGlobalProxy(ClientProxy.mc);
            proxy.updateDestroyBlockIcons();
        }
        proxy.setWorldAndLoadRenderers(ClientProxy.mc.world);
        PortalRenderer.renderLevel = 0;
        PortalRenderer.renderCount = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            prevCameraRoll = cameraRoll;
            cameraRoll *= 0.85F;
            if (Math.abs(cameraRoll) < 0.05F) {
                cameraRoll = 0F;
            }
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ImpLib.proxy.runAtMainThread(true, () -> {
            EventHandler.monitoredEntities[0].clear();
            PortalRenderer.renderLevel = 0;
            PortalRenderer.rollFactor.clear();
        });
    }
}
