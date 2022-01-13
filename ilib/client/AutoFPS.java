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
package ilib.client;

import ilib.ClientProxy;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import net.minecraft.client.Minecraft;
import net.minecraft.util.SoundCategory;

import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class AutoFPS {
    private static int waitingTime = -1;

    private static long lastActive;

    private static int backupFPS;
    private static float backupVolume;

    public static boolean reduceFPS = false, windowActive = true;

    private static long prevMouseTime, prevKeyTime, prevMoveTime;

    private static double prevX, prevY, prevZ;

    private static float prevYaw, prevPitch;

    public static final Minecraft mc = Minecraft.getMinecraft();

    public static void init(int time) {
        if (waitingTime == -1) {
            MinecraftForge.EVENT_BUS.register(AutoFPS.class);
            waitingTime = time * 1000;
            lastActive = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public static void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        int button = Mouse.getEventButton();
        int wheel = Mouse.getEventDWheel();
        if (button != -1 || wheel != 0) {
            windowActive = true;
            prevKeyTime = Keyboard.getEventNanoseconds();
            prevMouseTime = Mouse.getEventNanoseconds();
            recoveryFPS();
        }
    }

    @SubscribeEvent
    public static void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        windowActive = true;
        prevKeyTime = Keyboard.getEventNanoseconds();
        prevMouseTime = Mouse.getEventNanoseconds();
        recoveryFPS();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            boolean curWindowStatus = Display.isActive();
            if (!curWindowStatus) {
                if (windowActive) {
                    windowActive = false;
                    action();
                }
                return;
            } else if (!windowActive) {
                prevKeyTime = Keyboard.getEventNanoseconds();
                prevMouseTime = Mouse.getEventNanoseconds();
                windowActive = true;
            }

            if (mc.world == null) {
                recoveryFPS();
                return;
            }

            long currentKeyEventNanoSec = Keyboard.getEventNanoseconds();
            if (currentKeyEventNanoSec != prevKeyTime) {
                prevKeyTime = currentKeyEventNanoSec;
                recoveryFPS();
                return;
            }
/*
            try {
                MovementInput input = mc.player.movementInput;
                if (input.forwardKeyDown || input.backKeyDown || input.rightKeyDown || input.leftKeyDown || input.jump || input.sneak) {
                    recoveryFPS();
                    return;
                }
            } catch (NullPointerException ignored) {}*/

            long currMouseTime = Mouse.getEventNanoseconds();
            if (currMouseTime != prevMouseTime) {
                prevMouseTime = currMouseTime;
                recoveryFPS();
                return;
            }

//            if (!ignoreHoldButton)
//                for (int bc = 0; bc < Mouse.getButtonCount(); bc++) {
//                    if (Mouse.isButtonDown(bc)) {
//                        resetWaitingTimer();
//                        recoverFPS();
//                        break;
//                    }
//                }

            if (checkMovingSimply() || checkRotation()) {
                recoveryFPS();
                return;
            }
            checkWaitingTimer();
        }
    }

    private static boolean checkMoving() {
        int interval = 1000;
        long curMovingEventMilliSec = System.currentTimeMillis();
        if ((curMovingEventMilliSec - prevMoveTime >= 1000L) && mc.player != null) {
            double mx = mc.player.posX - prevX;
            double my = mc.player.posY - prevY;
            double mz = mc.player.posZ - prevZ;
            double movement = mx * mx + my * my + mz * mz;
            //ImpLib.logger().debug("movement:" + Math.sqrt(movement));
            prevX = mc.player.posX;
            prevY = mc.player.posY;
            prevZ = mc.player.posZ;
            prevMoveTime = curMovingEventMilliSec;
            double threshold = 0.9D;
            return (movement >= threshold * threshold);
        }
        return false;
    }

    private static boolean checkMovingSimply() {
        if (mc.player != null && (
                prevX != mc.player.posX || prevY != mc.player.posY || prevZ != mc.player.posZ)) {
            prevX = mc.player.posX;
            prevY = mc.player.posY;
            prevZ = mc.player.posZ;
            return true;
        }
        return false;
    }

    private static boolean checkRotation() {
        if (mc.player != null && (
                prevYaw != mc.player.rotationYaw || prevPitch != mc.player.rotationPitch)) {
            prevYaw = mc.player.rotationYaw;
            prevPitch = mc.player.rotationPitch;
            return true;
        }
        return false;
    }

    private static void checkWaitingTimer() {
        if (!reduceFPS &&
                System.currentTimeMillis() - lastActive > (waitingTime))
            action();
    }

    private static void action() {
        if (!reduceFPS && ClientProxy.mc.world != null) {
            backupFPS = mc.gameSettings.limitFramerate;
            mc.gameSettings.limitFramerate = 10;
            reduceFPS = true;

            backupVolume = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
            final float newVolume = 0.2f;
            mc.gameSettings.setSoundLevel(SoundCategory.MASTER, newVolume);
        }
    }

    private static void recoveryFPS() {
        if (reduceFPS) {
            //if(getWaitingTimeSecond() > )
            //PlayerUtil.sendToPlayer(null, "你这次AFK了 " + getWaitingTimeSecond() + " 秒");
            mc.gameSettings.limitFramerate = backupFPS;

            mc.gameSettings.setSoundLevel(SoundCategory.MASTER, backupVolume);

            reduceFPS = false;
        }
        lastActive = System.currentTimeMillis();
    }

    private static int getWaitingTimeSecond() {
        return (int) (System.currentTimeMillis() - lastActive) / 1000;
    }
}
