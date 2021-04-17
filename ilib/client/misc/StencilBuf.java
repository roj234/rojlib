package ilib.client.misc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import roj.opengl.FrameBuffer;
import roj.opengl.util.FboUtil;

import static ilib.ClientProxy.mc;

/**
 * @author Roj233
 * @since 2022/4/25 17:16
 */
public class StencilBuf {
	private int buf = -1;
	private int w, h;

	public void init() {
		if (buf == -1) buf = FboUtil.glGenRenderbuffers();

		int id = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
		if (id == buf) return;

		if (w != mc.displayWidth || h != mc.displayHeight) {
			FboUtil.glBindRenderbuffer(GL30.GL_RENDERBUFFER, buf);

			FboUtil.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_STENCIL_INDEX, mc.displayWidth, mc.displayHeight);
			w = mc.displayWidth;
			h = mc.displayHeight;

			FboUtil.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, buf);
			FrameBuffer.checkFBOState();
		}

		FboUtil.glBindRenderbuffer(GL30.GL_RENDERBUFFER, id);
	}

	public void delete() {
		if (buf < 0) return;
		FboUtil.glDeleteRenderbuffers(buf);
		buf = -1;
	}
}
