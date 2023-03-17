package ilib.asm.nx.client.async;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraftforge.fml.client.SplashProgress;
import net.minecraftforge.fml.common.FMLLog;

import java.util.concurrent.locks.Lock;

/**
 * @author Roj233
 * @since 2022/5/20 5:51
 */
@Nixim(value = "/")
class AsyncKillSplash extends SplashProgress {
	@Shadow("/")
	private static Drawable d;
	@Shadow("/")
	private static volatile boolean pause;
	@Shadow("/")
	private static Lock lock;
	@Shadow("/")
	private static boolean enabled;

	@Shadow("/")
	private static void checkThreadState() {}


	@Inject("/")
	public static void resume() {
		if (enabled) {
			checkThreadState();

			lock.unlock();
			pause = false;

			try {
				Display.getDrawable().releaseContext();
				d.makeCurrent();
			} catch (LWJGLException var1) {
				FMLLog.log.error("Error releasing GL context:", var1);
				throw new RuntimeException(var1);
			}
		}
	}
}
