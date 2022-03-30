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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: TimeUtil.java
 */
package ilib.util;

import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;

public class TimeUtil {
    public static long tick = 0;
    public static final ArrayList<String>    beginText = new ArrayList<>();

    public static int seconds() {
        return (int) (tick / 20);
    }

    public static int minutes() {
        return (int) (tick / 1200);
    }

    public static int hours() {
        return (int) (tick / 72000);
    }

    public static int second() {
        return (int) ((tick / 20) % 60);
    }

    public static int minute() {
        return (int) ((tick / 1200) % 60);
    }

    public static int hour() {
        return (int) ((tick / 72000) % 60);
    }

    public static boolean isSecond(int second) {
        return seconds() % second == 0;
    }

    public static boolean isMinute(int minute) {
        return minutes() % minute == 0;
    }

    public static boolean isHour(int hour) {
        return hours() % hour == 0;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onClientTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick = event.world.getTotalWorldTime();

            if (!beginText.isEmpty()) {
                for (String s : beginText)
                    PlayerUtil.sendTo(null, s);
                beginText.clear();
            }
        }
    }

    @SubscribeEvent
    @SideOnly(Side.SERVER)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick = DimensionManager.getWorld(0).getTotalWorldTime();
        }
    }

    static {
        MinecraftForge.EVENT_BUS.register(TimeUtil.class);
    }
}
