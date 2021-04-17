package roj.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

import java.util.function.Consumer;

import static roj.opengl.util.VboUtil.vboSupported;
import static roj.opengl.vertex.VertexFormat.Entry;

/**
 * Display Buffer
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class DisplayBuffer {
	private int vbo = -1, list = -1;
	private final VertexFormat vf;
	private final VertexBuilder vb;
	private final int mode;

	private final Consumer<VertexBuilder> drawer;
	private boolean drawn;

	protected DisplayBuffer(VertexFormat vf, VertexBuilder tmpVb, int mode) {
		this.vf = vf;
		this.vb = tmpVb;
		this.mode = mode;
		drawer = null;
	}

	public DisplayBuffer(VertexFormat vf, VertexBuilder tmpVb, int mode, Consumer<VertexBuilder> h) {
		this.vf = vf;
		this.vb = tmpVb;
		this.mode = mode;
		drawer = h;
	}

	protected void drawInternal(VertexBuilder vb) {
		drawer.accept(vb);
	}

	public final void init() {
		init(true);
	}

	public final void init(boolean compileOnly) {
		if (list < 0) {
			if ((list = GL11.glGenLists(1)) == 0) {
				int id = GL11.glGetError();
				String msg;
				if (id != 0) {
					msg = GLU.gluErrorString(id);
				} else {
					msg = "Unknown";
				}
				throw new IllegalStateException("Failed to generate list: " + msg);
			}
		}
		if (!vboSupported) {
			GL11.glNewList(list, compileOnly ? GL11.GL_COMPILE : GL11.GL_COMPILE_AND_EXECUTE);
		}

		VertexBuilder vb = this.vb;

		vb.begin(vf);

		drawInternal(vb);

		vb.end();
		if (vboSupported) {
			if (vbo < 0) vbo = VboUtil.glGenBuffers();

			// send to GPU
			VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, vbo);
			VboUtil.glBufferData(VboUtil.GL_ARRAY_BUFFER, vb.getBuffer(), VboUtil.GL_STATIC_DRAW);
			VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, 0);
			GL11.glNewList(list, compileOnly ? GL11.GL_COMPILE : GL11.GL_COMPILE_AND_EXECUTE);

			VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, vbo);

			VertexFormat.Entry[] list = vf.entries();

			int size = vf.getSize();
			for (Entry value : list) VboUtil.preDrawVBO(value, size);

			GL11.glDrawArrays(mode, 0, vb.getVertexCount());
			VboUtil.glBindBuffer(VboUtil.GL_ARRAY_BUFFER, 0);

			for (Entry entry : list) VboUtil.postDraw(entry);
		} else {
			VboUtil.drawCPUVertexes(mode, vb);
		}
		GL11.glEndList();
		drawn = true;
	}

	public void draw() {
		if (!drawn) init(false);
		else GL11.glCallList(list);
	}

	public void delete() {
		if (vbo >= 0) {
			VboUtil.glDeleteBuffers(vbo);
			vbo = -1;
		}
		if (list >= 0) {
			GL11.glDeleteLists(list, 1);
			list = -1;
		}
		drawn = false;
	}
}
