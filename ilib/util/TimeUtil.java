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
	public static final ArrayList<String> beginText = new ArrayList<>();

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
