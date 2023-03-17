package ilib.client;

import ilib.ImpLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class DisplayBuffer {
	private int bufferId = -1;
	private int vertexCount;
	private final VertexFormat vertexFormat;
	private final Consumer<BufferBuilder> drawable;

	private int mode;

	public static final boolean vboSupported = OpenGlHelper.vboSupported;

	public DisplayBuffer(VertexFormat format, Consumer<BufferBuilder> drawable) {
		this.vertexFormat = format;
		this.drawable = drawable;
	}

	@SuppressWarnings("fallthrough")
	public static void preDrawVBO(VertexFormatElement attr, VertexFormat format, int index) {
		int count = attr.getElementCount();
		int constant = attr.getType().getGlConstant();
		switch (attr.getUsage()) {
			case POSITION:
				GlStateManager.glVertexPointer(count, constant, index, format.getOffset(index));
				GlStateManager.glEnableClientState(32884);
				break;
			case COLOR:
				GlStateManager.glColorPointer(count, constant, index, format.getOffset(index));
				GlStateManager.glEnableClientState(32886);
				break;
			case UV:
				OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
				GlStateManager.glTexCoordPointer(count, constant, index, format.getOffset(index));
				GlStateManager.glEnableClientState(32888);
				OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
			case PADDING:
				break;
			case GENERIC:
				GL20.glEnableVertexAttribArray(attr.getIndex());
				GL20.glVertexAttribPointer(attr.getIndex(), count, constant, false, index, format.getOffset(index));
				break;
			default:
				ImpLib.logger().fatal("Unimplemented vanilla attribute upload: {}", attr.getUsage().getDisplayName());
		}

	}

	@SuppressWarnings("fallthrough")
	public static void postDrawVBO(VertexFormatElement attr) {
		switch (attr.getUsage()) {
			case POSITION:
				GlStateManager.glDisableClientState(32884);
				break;
			case COLOR:
				GlStateManager.glDisableClientState(32886);
				GlStateManager.resetColor();
				break;
			case UV:
				OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
				GlStateManager.glDisableClientState(32888);
				OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
			case PADDING:
				break;
			case GENERIC:
				GL20.glDisableVertexAttribArray(attr.getIndex());
				break;
			default:
				ImpLib.logger().fatal("Unimplemented vanilla attribute upload: {}", attr.getUsage().getDisplayName());
		}
	}

	public void firstDraw(int mode) {
		this.mode = mode;
		if (vboSupported) {
			if (this.bufferId < 0) {
				this.bufferId = OpenGlHelper.glGenBuffers();
			}
			OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, this.bufferId);
		} else {
			if (this.bufferId < 0) {
				this.bufferId = GLAllocation.generateDisplayLists(1);
			}
			GlStateManager.glNewList(this.bufferId, GL11.GL_COMPILE);
		}

		BufferBuilder builder = RenderUtils.BUILDER;
		builder.begin(mode, vertexFormat);

		doDraw(builder);

		if (vboSupported) {
			builder.finishDrawing();
			OpenGlHelper.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, builder.getByteBuffer(), GL15.GL_STATIC_DRAW);
			OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
			this.vertexCount = builder.getVertexCount();

			builder.reset();
		} else {
			Tessellator.getInstance().draw();
			GlStateManager.glEndList();
		}
	}

	protected void doDraw(BufferBuilder builder) {
		drawable.accept(builder);
	}

	public void draw() {
		if (vboSupported) {
			OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, this.bufferId);
			GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

			List<VertexFormatElement> list = vertexFormat.getElements();

			for (int i = 0, j = list.size(); i < j; i++) {
				VertexFormatElement element = list.get(i);
				preDrawVBO(element, vertexFormat, i);
			}

			GlStateManager.glDrawArrays(mode, 0, this.vertexCount);
			OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);

			for (int i = 0, j = list.size(); i < j; i++) {
				postDrawVBO(list.get(i));
			}
		} else {
			GL11.glCallList(this.bufferId);
		}
	}

	public void deleteGlBuffers() {
		if (this.bufferId >= 0) {
			if (vboSupported) OpenGlHelper.glDeleteBuffers(this.bufferId);
			else GLAllocation.deleteDisplayLists(this.bufferId);
			this.bufferId = -1;
		}
	}

	public boolean isEmpty() {
		return bufferId < 0;
	}
}
