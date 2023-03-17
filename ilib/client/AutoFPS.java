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

	private static int backupFPS;
	private static float backupVolume;
	private static boolean backupVSync;

	public static final Minecraft mc = Minecraft.getMinecraft();

	private static boolean checkActive() {
		// 防止未响应
		Display.processMessages();
		if (!Display.isVisible()) return false;

		if (Display.isActive() || Mouse.isInsideWindow()) {
			long t1 = Keyboard.getEventNanoseconds()/1000000;
			long t2 = Mouse.getEventNanoseconds()/1000000;
			return System.currentTimeMillis() - t1 <= waitingTime &&
					System.currentTimeMillis() - t2 <= waitingTime;
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
		if (backupFPS == -114514) {
			backupFPS = mc.gameSettings.limitFramerate;
			mc.gameSettings.limitFramerate = targetFPS;
			if (mc.gameSettings.enableVsync) {
				backupVSync = true;
				Display.setVSyncEnabled(false);
			}

			if (targetVol < 1) {
				backupVolume = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
				mc.gameSettings.setSoundLevel(SoundCategory.MASTER, targetVol);
			}
		}
	}

	private static void fpsUp() {
		if (backupFPS != -114514) {
			mc.gameSettings.limitFramerate = backupFPS;
			if (backupVSync) Display.setVSyncEnabled(true);

			if (targetVol < 1) {
				mc.gameSettings.setSoundLevel(SoundCategory.MASTER, backupVolume);
			}

			backupFPS = -114514;
		}
	}
}
