package roj.opengl.util;

import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;

/**
 * @author Roj233
 * @since 2021/9/18 15:12
 */
public class MultiTexUtil {
	public static final int GL_TEXTURE0 = 33984;

	public static final boolean arbMultitexture;

	public static void initMultiTextures() {}

	static {
		ContextCapabilities cap = GLContext.getCapabilities();

		arbMultitexture = cap.GL_ARB_multitexture && !cap.OpenGL13;
		if (arbMultitexture) {
			//System.out.println("MultiTex: OK, Using ARB");
		} else if (cap.OpenGL13) {
			//System.out.println("MultiTex: OK, Using GL 1.3");
		} else {
			System.out.println("MultiTex: ERROR");
		}
	}

	public static void setActiveTexture(int texture) {
		if (arbMultitexture) {
			ARBMultitexture.glActiveTextureARB(texture);
		} else {
			GL13.glActiveTexture(texture);
		}
	}

	public static void setClientActiveTexture(int texture) {
		if (arbMultitexture) {
			ARBMultitexture.glClientActiveTextureARB(texture);
		} else {
			GL13.glClientActiveTexture(texture);
		}
	}
}
