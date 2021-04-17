package ilib.client.light;

import ilib.client.event.RenderStageEvent;
import ilib.client.shader.Shader;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;

/**
 * @author Roj233
 * @since 2022/5/17 3:22
 */
public class GlobalShader {
	public static final Shader _8BIT = new Shader();
	public static final Shader RANDOM_MOVE = new Shader();

	static {
		try {
			_8BIT.compile(null, "assets/ilib/shaders/8bit.fsh", true);
			RANDOM_MOVE.compile("assets/ilib/shaders/random.vsh", null, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		MinecraftForge.EVENT_BUS.register(GlobalShader.class);
	}

	public static void select(Shader s) {
		if (shader != null) shader.unbind();
		shader = s;
	}

	private static Shader shader = RANDOM_MOVE;

	@SubscribeEvent
	public static void onRender(RenderStageEvent event) {
		if (event.stage == RenderStageEvent.Stage.OPAQUE) {
			shader.bind();
			int id = shader.id;

			glUniform1f(glGetUniformLocation(id, "rand"), 1);
			glUniform1f(glGetUniformLocation(id, "ticks"), 0);//(int)TimeUtil.tick / 30f);

			//glUniform1i(glGetUniformLocation(id, "sampler"), 0);
			//glUniform1i(glGetUniformLocation(id, "lightmap"), 1);
		} else {
			shader.unbind();
		}
	}
}
