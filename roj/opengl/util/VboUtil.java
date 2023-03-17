//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package roj.opengl.util;

import org.lwjgl.opengl.*;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

import java.nio.ByteBuffer;

import static roj.opengl.vertex.VertexFormat.*;

/**
 * Vertex buffer util
 *
 * @author Roj234
 * @since 2021/9/18 13:06
 */
public class VboUtil {
	public static final int GL_ARRAY_BUFFER;
	public static final int GL_STATIC_DRAW;
	public static final int GL_VERTEX_ARRAY_BINDING;

	public static final boolean vboSupported;
	public static final boolean arbVbo;

	public static void initVertexbuffers() {}

	static {
		ContextCapabilities cap = GLContext.getCapabilities();

		arbVbo = !cap.OpenGL15 && cap.GL_ARB_vertex_buffer_object;
		if (vboSupported = cap.OpenGL15 || arbVbo) {
			//            if (arbVbo) {
			//                System.out.println("VBO: OK, Using ARB");
			//            } else {
			//                System.out.println("VBO: OK, Using GL 1.5");
			//            }
			GL_STATIC_DRAW = 35044;
			GL_ARRAY_BUFFER = 34962;
		} else {
			System.out.println("VBO: ERROR");
			GL_STATIC_DRAW = 0;
			GL_ARRAY_BUFFER = 0;
		}

		if (cap.OpenGL30 || cap.GL_ARB_vertex_array_object) {
			GL_VERTEX_ARRAY_BINDING = GL30.GL_VERTEX_ARRAY_BINDING;
		} else {
			GL_VERTEX_ARRAY_BINDING = 0;
		}
	}

	public static int glGenBuffers() {
		return arbVbo ? ARBVertexBufferObject.glGenBuffersARB() : GL15.glGenBuffers();
	}

	public static void glBindBuffer(int target, int buffer) {
		if (arbVbo) {
			ARBVertexBufferObject.glBindBufferARB(target, buffer);
		} else {
			GL15.glBindBuffer(target, buffer);
		}
	}

	public static void glBufferData(int target, ByteBuffer data, int usage) {
		if (arbVbo) {
			ARBVertexBufferObject.glBufferDataARB(target, data, usage);
		} else {
			GL15.glBufferData(target, data, usage);
		}
	}

	public static void glDeleteBuffers(int buffer) {
		if (arbVbo) {
			ARBVertexBufferObject.glDeleteBuffersARB(buffer);
		} else {
			GL15.glDeleteBuffers(buffer);
		}
	}

	public static int glGenVertexArrays() {
		return GL30.glGenVertexArrays();
	}

	public static void glBindVertexArray(int id) {
		GL30.glBindVertexArray(id);
	}

	public static void preDrawVBO(VertexFormat.Entry attr, int stride) {
		int count = attr.elementCount();
		int type = attr.glType();
		switch (attr.usage()) {
			case POS:
				GL11.glVertexPointer(count, type, stride, attr.getOffset());
				GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
				break;
			case NORMAL:
				GL11.glNormalPointer(type, stride, attr.getOffset());
				GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
				break;
			case COLOR:
				GL11.glColorPointer(count, type, stride, attr.getOffset());
				GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
				break;
			case UV:
				MultiTexUtil.setClientActiveTexture(MultiTexUtil.GL_TEXTURE0 + attr.getIndex());
				GL11.glTexCoordPointer(count, type, stride, attr.getOffset());
				GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
				MultiTexUtil.setClientActiveTexture(MultiTexUtil.GL_TEXTURE0);
				break;
			case PADDING:
				break;
			case GENERIC:
				GL20.glEnableVertexAttribArray(attr.getIndex());
				GL20.glVertexAttribPointer(attr.getIndex(), count, type, false, stride, attr.getOffset());
				break;
			default:
				throw new RuntimeException("Unknown how to process " + attr.usage());
		}
	}

	private static void preDrawCPU(VertexFormat.Entry attr, int stride, ByteBuffer data) {
		int count = attr.elementCount();
		int type = attr.glType();
		switch (attr.usage()) {
			case POS:
				GL11.glVertexPointer(count, type, stride, data);
				GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
				break;
			case NORMAL:
				GL11.glNormalPointer(type, stride, data);
				GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
				break;
			case COLOR:
				GL11.glColorPointer(count, type, stride, data);
				GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
				break;
			case UV:
				MultiTexUtil.setClientActiveTexture(MultiTexUtil.GL_TEXTURE0 + attr.getIndex());
				GL11.glTexCoordPointer(count, type, stride, data);
				GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
				MultiTexUtil.setClientActiveTexture(MultiTexUtil.GL_TEXTURE0);
				break;
			case PADDING:
				break;
			case GENERIC:
				GL20.glEnableVertexAttribArray(attr.getIndex());
				GL20.glVertexAttribPointer(attr.getIndex(), count, type, false, stride, data);
				break;
			default:
				throw new RuntimeException("Unknown how to process " + attr.usage());
		}
	}

	public static void postDraw(Entry attr) {
		switch (attr.usage()) {
			case POS:
				GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
				break;
			case NORMAL:
				GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
				break;
			case COLOR:
				GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
				break;
			case UV:
				MultiTexUtil.setClientActiveTexture(MultiTexUtil.GL_TEXTURE0 + attr.getIndex());
				GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
				MultiTexUtil.setClientActiveTexture(MultiTexUtil.GL_TEXTURE0);
				break;
			case PADDING:
				break;
			case GENERIC:
				GL20.glDisableVertexAttribArray(attr.getIndex());
				break;
		}
	}

	public static void drawCPUVertexes(int mode, VertexBuilder vb) {
		if (vb.getVertexCount() > 0) {
			VertexFormat format = vb.getVertexFormat();

			ByteBuffer buf = vb.getBuffer();
			VertexFormat.Entry[] list = format.entries();

			int size = format.getSize();
			for (VertexFormat.Entry value : list) {
				buf.position(value.getOffset());
				preDrawCPU(value, size, buf);
			}

			GL11.glDrawArrays(mode, 0, vb.getVertexCount());

			for (VertexFormat.Entry entry : list) {
				postDraw(entry);
			}
		}

		vb.reset();
	}
}
