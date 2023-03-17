package roj.opengl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.glu.GLU;
import roj.io.NIOUtil;
import roj.opengl.util.FboUtil;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;

public final class FrameBuffer {
	private int texWidth, texHeight;
	private int idFrameBuf = -1, idDepthBuf = -1, idTexture = -1;

	private final IntBuffer lastViewport = BufferUtils.createIntBuffer(16);

	private int idTextureLast, idFrameBufLast;

	public FrameBuffer(int w, int h) {
		this.texWidth = w;
		this.texHeight = h;
	}

	public int getIdTexture() {
		return idTexture;
	}

	public static void checkGlErrors(String message) {
		int error = glGetError();
		if (error != 0) {
			String name = GLU.gluErrorString(error);

			System.out.println("########## GL ERROR ##########");
			System.out.println("@" + message);
			System.out.println(error + ": " + name);
		}
	}

	public void resize(int w, int h) {
		deleteFBO();
		this.texWidth = w;
		this.texHeight = h;
	}

	public void begin() {
		if (this.idFrameBuf == -1) {
			createFBO();
		}
		checkGlErrors("FBO Begin Init");
		checkFBOState();

		this.idFrameBufLast = glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

		FboUtil.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.idFrameBuf);

		this.lastViewport.clear();
		glGetInteger(GL_VIEWPORT, this.lastViewport);
		glViewport(0, 0, this.texWidth, this.texHeight);

		if (false) {
			glMatrixMode(GL_PROJECTION);
			glPushMatrix();
			glLoadIdentity();
		}

		glMatrixMode(GL_MODELVIEW);

		glPushMatrix();
		glLoadIdentity();
		this.idTextureLast = glGetInteger(GL_TEXTURE_BINDING_2D);
		glBindTexture(GL_TEXTURE_2D, this.idTexture);

		glClearColor(0, 0, 0, 0);
		glPushAttrib(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		//glCullFace(GL_FRONT);
		//glEnable(GL_LIGHTING);
		glEnable(GL_DEPTH_TEST);
		glEnable(GL12.GL_RESCALE_NORMAL);

		checkFBOState();
		checkGlErrors("FBO Begin Final");
	}

	public void end() {
		if (this.idFrameBuf == -1) {
			return;
		}
		checkGlErrors("FBO End Init");

		//glCullFace(GL_BACK);
		//glDisable(GL_LIGHTING);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL12.GL_RESCALE_NORMAL);
		glPopAttrib();

		if (false) {
			glMatrixMode(GL_PROJECTION);
			glPopMatrix();
		}
		glMatrixMode(GL_MODELVIEW);
		glPopMatrix();
		glViewport(this.lastViewport.get(0), this.lastViewport.get(1), this.lastViewport.get(2), this.lastViewport.get(3));

		FboUtil.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.idFrameBufLast);
		glBindTexture(GL_TEXTURE_2D, this.idTextureLast);

		checkGlErrors("FBO End Final");
	}

	public void render() {
		if (this.idFrameBuf == -1) {
			return;
		}
		this.idTextureLast = glGetInteger(GL_TEXTURE_BINDING_2D);
		glEnable(GL_COLOR_MATERIAL);
		glDisable(GL_ALPHA_TEST);

		glBindTexture(GL_TEXTURE_2D, this.idTexture);
		glBegin(GL_QUADS);

		glVertex2i(0, texHeight / 16);
		glTexCoord2f(0, 0);
		glVertex2i(texWidth / 16, texHeight / 16);
		glTexCoord2f(1, 0);
		glVertex2i(texWidth / 16, 0);
		glTexCoord2f(1, 1);
		glVertex2i(0, 0);
		glTexCoord2f(0, 1);

		glEnd();
		getImage();
		glBindTexture(GL_TEXTURE_2D, this.idTextureLast);
	}

	public void bind() {
		glBindTexture(GL_TEXTURE_2D, this.idTexture);
	}

	public void restoreTexture() {
		glBindTexture(GL_TEXTURE_2D, this.idTextureLast);
	}

	@Nonnull
	public IntBuffer getImage() {
		if (this.idFrameBuf == -1) {
			return ByteBuffer.allocateDirect(0).asIntBuffer();
		}
		glBindTexture(GL_TEXTURE_2D, this.idTexture);

		glPixelStorei(GL_PACK_ALIGNMENT, 1);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

		int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH), h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);

		IntBuffer texture = createIntBuffer(w * h);
		glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, texture);
		texture.position(w * h);
		return texture;
	}

	static IntBuffer buf;

	private static IntBuffer createIntBuffer(int size) {
		if (buf == null || buf.capacity() < size) {
			NIOUtil.clean(buf);
			buf = null;
			return buf = BufferUtils.createIntBuffer(size);
		} else {
			buf.clear();
			return buf;
		}
	}

	private void createFBO() {
		if (this.idFrameBuf != -1) {
			deleteFBO();
		}

		final int currFBO = glGetInteger(GL30.GL_FRAMEBUFFER_BINDING), currTex = glGetInteger(GL_TEXTURE_BINDING_2D);

		FboUtil.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.idFrameBuf = FboUtil.glGenFramebuffers());

		glBindTexture(GL_TEXTURE_2D, this.idTexture = glGenTextures());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, this.texWidth, this.texHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

		glBindTexture(GL_TEXTURE_2D, currTex);

		FboUtil.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.idDepthBuf = FboUtil.glGenRenderbuffers());
		FboUtil.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL_DEPTH_COMPONENT, this.texWidth, this.texHeight);
		FboUtil.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, this.idDepthBuf);
		FboUtil.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.idTexture, 0);

		FboUtil.glBindFramebuffer(GL30.GL_FRAMEBUFFER, currFBO);
	}

	public static void checkFBOState() {
		int i = FboUtil.glCheckFramebufferStatus(FboUtil.GL_FRAMEBUFFER);
		if (i != FboUtil.GL_FRAMEBUFFER_COMPLETE) {
			if (i == FboUtil.GL_FB_INCOMPLETE_ATTACHMENT) {
				throw new RuntimeException("GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT");
			} else if (i == FboUtil.GL_FB_INCOMPLETE_MISS_ATTACH) {
				throw new RuntimeException("GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT");
			} else if (i == FboUtil.GL_FB_INCOMPLETE_DRAW_BUFFER) {
				throw new RuntimeException("GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER");
			} else if (i == FboUtil.GL_FB_INCOMPLETE_READ_BUFFER) {
				throw new RuntimeException("GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER");
			} else {
				throw new RuntimeException("glCheckFramebufferStatus returned unknown status:" + i);
			}
		}
	}

	public void deleteFBO() {
		if (this.idFrameBuf != -1) {
			FboUtil.glDeleteFramebuffers(this.idFrameBuf);
			glDeleteTextures(this.idTexture);
			FboUtil.glDeleteRenderbuffers(this.idDepthBuf);

			this.idDepthBuf = -1;
			this.idFrameBuf = -1;
			this.idTexture = -1;
		}
	}
}
