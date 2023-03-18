package ilib.asm.nx.client;

import ilib.client.AutoFPS;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

/**
 * @author Roj234
 * @since 2023/4/8 0008 19:14
 */
@Nixim("org.lwjgl.opengl.Sync")
final class AutoFPSHook {
	@Inject(at = Inject.At.INVOKE, param = {"java.lang.Thread.sleep", "sleepHook"}, value = "/")
	public static void sync(int fps) {}

	@Copy
	private static void sleepHook(long time) throws InterruptedException {
		if (AutoFPS.checkActive()) {
			nextFrame = getTime();
			return;
		}

		Thread.sleep(time);
	}

	@Shadow("/")
	private static long nextFrame;
	@Shadow("/")
	private static long getTime() { return 0; }
}
