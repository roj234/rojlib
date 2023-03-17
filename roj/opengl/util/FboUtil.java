//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package roj.opengl.util;

import org.lwjgl.opengl.*;

/**
 * Frame buffer util
 *
 * @author Roj234
 * @since 2021/9/17 23:15
 */
public class FboUtil {
	public static final int GL_FRAMEBUFFER = 36160;
	public static final int GL_RENDERBUFFER = 36161;
	public static final int GL_COLOR_ATTACHMENT0 = 36064;
	public static final int GL_DEPTH_ATTACHMENT = 36096;
	public static final int GL_FRAMEBUFFER_COMPLETE = 36053;
	public static final int GL_FB_INCOMPLETE_ATTACHMENT = 36054;
	public static final int GL_FB_INCOMPLETE_MISS_ATTACH = 36055;
	public static final int GL_FB_INCOMPLETE_DRAW_BUFFER = 36059;
	public static final int GL_FB_INCOMPLETE_READ_BUFFER = 36060;

	private static final int mode;
	private static final int BASE = 0, ARB = 1, EXT = 2, NONE = 3;

	public static void initFramebuffers() {}

	static {
		ContextCapabilities cap = GLContext.getCapabilities();

		if ((cap.OpenGL14 || cap.GL_EXT_blend_func_separate) && (cap.GL_ARB_framebuffer_object || cap.GL_EXT_framebuffer_object || cap.OpenGL30)) {
			if (cap.OpenGL30) {
				mode = BASE;
			} else if (cap.GL_ARB_framebuffer_object) {
				mode = ARB;
			} else {
				mode = EXT;
			}
			System.out.println("FBO: OK, Mode=" + mode);
		} else {
			mode = NONE;
			System.out.println("FBO: ERROR");
		}
	}

	public static void glBindFramebuffer(int target, int framebufferIn) {
		switch (mode) {
			case BASE:
				GL30.glBindFramebuffer(target, framebufferIn);
				break;
			case ARB:
				ARBFramebufferObject.glBindFramebuffer(target, framebufferIn);
				break;
			case EXT:
				EXTFramebufferObject.glBindFramebufferEXT(target, framebufferIn);
		}

	}

	public static void glBindRenderbuffer(int target, int renderbuffer) {
		switch (mode) {
			case BASE:
				GL30.glBindRenderbuffer(target, renderbuffer);
				break;
			case ARB:
				ARBFramebufferObject.glBindRenderbuffer(target, renderbuffer);
				break;
			case EXT:
				EXTFramebufferObject.glBindRenderbufferEXT(target, renderbuffer);
		}

	}

	public static void glDeleteRenderbuffers(int renderbuffer) {
		switch (mode) {
			case BASE:
				GL30.glDeleteRenderbuffers(renderbuffer);
				break;
			case ARB:
				ARBFramebufferObject.glDeleteRenderbuffers(renderbuffer);
				break;
			case EXT:
				EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffer);
		}

	}

	public static void glDeleteFramebuffers(int framebufferIn) {
		switch (mode) {
			case BASE:
				GL30.glDeleteFramebuffers(framebufferIn);
				break;
			case ARB:
				ARBFramebufferObject.glDeleteFramebuffers(framebufferIn);
				break;
			case EXT:
				EXTFramebufferObject.glDeleteFramebuffersEXT(framebufferIn);
		}

	}

	public static int glGenFramebuffers() {
		switch (mode) {
			case BASE:
				return GL30.glGenFramebuffers();
			case ARB:
				return ARBFramebufferObject.glGenFramebuffers();
			case EXT:
				return EXTFramebufferObject.glGenFramebuffersEXT();
			default:
				return -1;
		}
	}

	public static int glGenRenderbuffers() {
		switch (mode) {
			case BASE:
				return GL30.glGenRenderbuffers();
			case ARB:
				return ARBFramebufferObject.glGenRenderbuffers();
			case EXT:
				return EXTFramebufferObject.glGenRenderbuffersEXT();
			default:
				return -1;
		}
	}

	public static void glRenderbufferStorage(int target, int internalFormat, int width, int height) {
		switch (mode) {
			case BASE:
				GL30.glRenderbufferStorage(target, internalFormat, width, height);
				break;
			case ARB:
				ARBFramebufferObject.glRenderbufferStorage(target, internalFormat, width, height);
				break;
			case EXT:
				EXTFramebufferObject.glRenderbufferStorageEXT(target, internalFormat, width, height);
		}

	}

	public static void glFramebufferRenderbuffer(int target, int attachment, int renderBufferTarget, int renderBuffer) {
		switch (mode) {
			case BASE:
				GL30.glFramebufferRenderbuffer(target, attachment, renderBufferTarget, renderBuffer);
				break;
			case ARB:
				ARBFramebufferObject.glFramebufferRenderbuffer(target, attachment, renderBufferTarget, renderBuffer);
				break;
			case EXT:
				EXTFramebufferObject.glFramebufferRenderbufferEXT(target, attachment, renderBufferTarget, renderBuffer);
		}

	}

	public static int glCheckFramebufferStatus(int target) {
		switch (mode) {
			case BASE:
				return GL30.glCheckFramebufferStatus(target);
			case ARB:
				return ARBFramebufferObject.glCheckFramebufferStatus(target);
			case EXT:
				return EXTFramebufferObject.glCheckFramebufferStatusEXT(target);
			default:
				return -1;
		}
	}

	public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
		switch (mode) {
			case BASE:
				GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
				break;
			case ARB:
				ARBFramebufferObject.glFramebufferTexture2D(target, attachment, textarget, texture, level);
				break;
			case EXT:
				EXTFramebufferObject.glFramebufferTexture2DEXT(target, attachment, textarget, texture, level);
		}
	}
}
