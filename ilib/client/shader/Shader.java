package ilib.client.shader;

import ilib.ImpLib;
import org.lwjgl.opengl.GL20;
import roj.io.IOUtil;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2021/5/27 23:37
 */
public class Shader {
	public int id = -1;

	public void compile(String vsh, String fsh, boolean isPath) throws Exception {
		if (this.id != -1) {
			throw new RuntimeException("Already create!");
		}

		this.id = GL20.glCreateProgram();

		int vertex = vsh == null ? 0 : createShader(vsh, GL20.GL_VERTEX_SHADER, isPath);
		int fragment = fsh == null ? 0 : createShader(fsh, GL20.GL_FRAGMENT_SHADER, isPath);

		GL20.glLinkProgram(this.id);

		if (GL20.glGetProgrami(this.id, GL20.GL_LINK_STATUS) == 0) {
			throw new RuntimeException("Link shader: " + GL20.glGetProgramInfoLog(this.id, 1024));
		}

		if (vertex != 0) {
			GL20.glDetachShader(this.id, vertex);
		}

		if (fragment != 0) {
			GL20.glDetachShader(this.id, fragment);
		}

		GL20.glValidateProgram(this.id);

		if (GL20.glGetProgrami(this.id, GL20.GL_VALIDATE_STATUS) == 0) {
			ImpLib.logger().error("Validate shader: " + GL20.glGetProgramInfoLog(this.id, 1024));
		}

		GL20.glDeleteShader(vertex);
		GL20.glDeleteShader(fragment);
	}

	/**
	 * Shared code for compiling a shader program's shader
	 */
	protected int createShader(String shader, int shaderType, boolean isPath) throws IOException {
		shader = isPath ? IOUtil.readUTF(shader) : shader;
		int id = GL20.glCreateShader(shaderType);

		if (id == 0) throw new RuntimeException("Shader type: " + shaderType);

		GL20.glShaderSource(id, shader);
		GL20.glCompileShader(id);

		if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
			throw new RuntimeException("Shader compile: " + GL20.glGetShaderInfoLog(id, 1024));
		}

		GL20.glAttachShader(this.id, id);

		return id;
	}

	public void bind() {
		GL20.glUseProgram(this.id);
	}

	public void clear() {
		if (this.id != -1) {
			GL20.glDeleteProgram(this.id);
			this.id = -1;
		}
	}

	public void unbind() {
		GL20.glUseProgram(0);
	}
}