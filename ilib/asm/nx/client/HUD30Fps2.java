package ilib.asm.nx.client;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

/**
 * @author Roj234
 * @since 2022/9/26 0026 17:27
 */
@Nixim(value = "/", copyItf = true)
public class HUD30Fps2 extends Minecraft implements HUD30Fps3 {
	private static final float MY_FPS = 31;

	HUD30Fps2() {
		super(null);
	}

	@Copy(unique = true)
	private long lastRenderTime;
	@Copy(unique = true)
	private Framebuffer hudFBO;
	@Inject(at = Inject.At.INVOKE, param = {"net.minecraft.client.renderer.OpenGlHelper.func_77474_a", "initTex_replace"})
	private void init() {}
	@Copy
	// 2022/09/26 新增！INVOKE可以把静态方法调用换成virtual，或者加上任意数量的形参ID
	private void initTex_replace() {
		OpenGlHelper.initializeTextures();
		hudFBO = new Framebuffer(displayWidth, displayHeight, true);
		hudFBO.setFramebufferColor(0,0,0,0);
	}

	@Inject(at = Inject.At.TAIL)
	private void updateFramebufferSize() {
		hudFBO.createBindFramebuffer(displayWidth, displayHeight);
	}

	@Inject
	private void displayDebugInfo(long elapsedTicksTime) {}

	@Override
	@Copy
	public Framebuffer H33_getFBO() {
		return hudFBO;
	}

	@Override
	public boolean H33_getMode() {
		long t = System.currentTimeMillis();
		if (t - lastRenderTime > 1000f / MY_FPS) {
			lastRenderTime = t;
			return true;
		}
		return false;
	}
}
