package ilib.client;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import net.minecraft.client.Minecraft;
import net.minecraft.util.SoundCategory;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class AutoFPS {
	private static int waitingTime = -1;

	private static int targetFPS;
	private static float targetVol;

	private static boolean isDown;
	private static long hwts, ts;

	private static int backupFPS;
	private static float backupVolume;
	private static boolean backupVSync;

	public static final Minecraft mc = Minecraft.getMinecraft();

	public static boolean checkActive() {
		// 防止未响应
		Display.processMessages();
		if (!Display.isVisible()) return false;

		long t = Math.max(Keyboard.getEventNanoseconds(), Mouse.getEventNanoseconds());
		if (t > hwts) {
			hwts = t;
			ts = System.currentTimeMillis();
		}

		if (Display.isActive() || Mouse.isInsideWindow()) {
			return System.currentTimeMillis() - ts < waitingTime;
		}
		return false;
	}

	public static void init(int time, int targetFPS1, float targetVol1) {
		if (waitingTime == -1) {
			MinecraftForge.EVENT_BUS.register(AutoFPS.class);
			waitingTime = time * 1000;
			targetFPS = targetFPS1;
			targetVol = targetVol1;
		}
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			if (checkActive()) {
				fpsUp();
			} else {
				fpsDown();
			}
		}
	}

	private static void fpsDown() {
		if (!isDown) {
			backupFPS = mc.gameSettings.limitFramerate;
			mc.gameSettings.limitFramerate = targetFPS;
			if (backupVSync = mc.gameSettings.enableVsync) {
				Display.setVSyncEnabled(false);
			}

			if (targetVol < 1) {
				backupVolume = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
				mc.gameSettings.setSoundLevel(SoundCategory.MASTER, targetVol);
			}

			isDown = true;
		}
	}

	private static void fpsUp() {
		if (isDown) {
			mc.gameSettings.limitFramerate = backupFPS;
			if (backupVSync) Display.setVSyncEnabled(true);

			if (targetVol < 1) {
				mc.gameSettings.setSoundLevel(SoundCategory.MASTER, backupVolume);
			}

			isDown = false;
		}
	}
}
