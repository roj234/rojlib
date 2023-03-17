package ilib.asm.nx.client;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.NiximSystem;

import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;

/**
 * @author Roj234
 * @since 2022/9/26 0026 17:27
 */
@Nixim("/")
public class HUD30Fps extends EntityRenderer {
	HUD30Fps() {
		super(null, null);
	}

	@Inject(at = Inject.At.MIDDLE, param = "replaceSeg_RunGameLoop")
	public void updateCameraAndRender(float partialTicks, long nanoTime) {
		HUD30Fps3 mc1 = (HUD30Fps3) mc;
		Framebuffer fbo = mc1.H33_getFBO();

		int prev = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
		if (mc1.H33_getMode()) {
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo.framebufferObject);
			mc.ingameGUI.renderGameOverlay(partialTicks);
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prev);
		}

		fbo.bindFramebufferTexture();
		Tessellator t = Tessellator.getInstance();
		BufferBuilder bb = t.getBuffer();
		bb.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);

		float f = mc.displayWidth;
		float f1 = mc.displayHeight;
		bb.pos(0, f1, 0).tex(0, 0).color(255, 255, 255, 255).endVertex();
		bb.pos(f, f1, 0).tex(1, 0).color(255, 255, 255, 255).endVertex();
		bb.pos(f, 0, 0).tex(1, 1).color(255, 255, 255, 255).endVertex();
		bb.pos(0, 0, 0).tex(0, 1).color(255, 255, 255, 255).endVertex();
		t.draw();

		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prev);
	}
	private void matchSegment(float partialTicks, long nanoTime) {
		if (!mc.gameSettings.hideGUI || mc.currentScreen != null) {
			GlStateManager.alphaFunc(516, 0.1F);
			setupOverlayRendering();
			renderItemActivation(NiximSystem.SpecMethods.$$$VALUE_I(), NiximSystem.SpecMethods.$$$VALUE_I(), partialTicks);
			NiximSystem.SpecMethods.$$$MATCH_TARGET_BEGIN();
			mc.ingameGUI.renderGameOverlay(partialTicks);
			NiximSystem.SpecMethods.$$$MATCH_TARGET_END();
		}
		NiximSystem.SpecMethods.$$$MATCH_END();
	}
}
