package ilib.client.shader;

import ilib.client.event.RenderStageEvent;
import ilib.util.TimeUtil;
import org.lwjgl.opengl.Display;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static org.lwjgl.opengl.GL20.*;

/**
 * @author Roj234
 * @since 2022/9/4 0004 10:08
 */
public class BadCamShader extends Shader {
	{
		try {
			compile("assets/ilib/shader/badcam.vsh", "assets/ilib/shader/badcam.fsh", true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SubscribeEvent
	public void render(RenderStageEvent event) {
		if (event.stage == RenderStageEvent.Stage.TRANSLUCENT) {
			bind();

			glUniform2f(glGetUniformLocation(id, "resolution"), Display.getWidth(), Display.getHeight());
			glUniform1f(glGetUniformLocation(id, "time"), (int) TimeUtil.tick / 30f);

			glUniform1i(glGetUniformLocation(id, "texture"), 0);
		} else {
			unbind();
		}
	}
}
