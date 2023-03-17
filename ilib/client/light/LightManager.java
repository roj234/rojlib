package ilib.client.light;

import ilib.client.event.RenderStageEvent;
import ilib.client.shader.Shader;
import ilib.misc.MutAABB;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Comparator;

import static org.lwjgl.opengl.GL20.*;

/**
 * @author Roj233
 * @since 2022/5/17 3:22
 */
//@Mod.EventBusSubscriber(modid = "ilib")
public class LightManager {
	public static final MyHashSet<Light> lights = new MyHashSet<>();

	private static final SimpleList<Light> tmp1 = new SimpleList<>();
	private static final SimpleList<Light> tmp2 = new SimpleList<>();
	private static final MutAABB box = new MutAABB();

	private static double distanceSq(float x1, float y1, float z1, double x2, double y2, double z2) {
		x1 -= x2;
		x1 *= x1;
		y1 -= y2;
		x1 += y1 * y1;
		z1 -= z2;
		return x1 + z1 * z1;
	}

	private static final Comparator<Light> cmp = (a, b) -> {
		EntityPlayerSP p = Minecraft.getMinecraft().player;
		double d1 = distanceSq(a.x, a.y, a.z, p.posX, p.posY, p.posZ);
		double db = distanceSq(b.x, b.y, b.z, p.posX, p.posY, p.posZ);
		return Double.compare(d1, db);
	};

	private static int o_lightCount, o_lightArray, o_playerPos;
	private static final Shader lightShader = new Shader();

	public static void addLight(Light l) {
		lights.add(l);
	}

	public static void removeLight(Light l) {
		lights.remove(l);
	}

	public static void clearLight() {
		lights.clear();
	}

	static {
		lightShader.clear();

		try {
			lightShader.compile("assets/ilib/shaders/mylight.vsh", "assets/ilib/shaders/mylight.fsh", true);
		} catch (Exception e) {
			e.printStackTrace();
			lightShader.clear();
		}
	}

	@SubscribeEvent
	public static void onRender(RenderStageEvent event) {
		if (event.stage == RenderStageEvent.Stage.OPAQUE) {
			lightShader.bind();
			int id = lightShader.id;

			glUniform1i(glGetUniformLocation(id, "sampler"), 0);
			glUniform1i(glGetUniformLocation(id, "lightmap"), 1);

			SimpleList<Light> all = tmp1;
			all.clear();
			all.addAll(lights);
			all.add(new Light().pos(0, 1, 0).color(1, 0, 0, 1).radius(99));

			if (MinecraftForge.EVENT_BUS.post(new GetLightEvent(all))) return;

			SimpleList<Light> display = tmp2;
			display.clear();
			for (int i = 0; i < all.size(); i++) {
				Light l = all.get(i);
				l.getBoundBox(box);
				//if (culler != null && culler.isBoundingBoxInFrustum(box)) {
				display.add(l);
				//}
			}
			display.sort(cmp);

			glUniform1i(glGetUniformLocation(id, "lightCount"), Math.min(64, display.size()));

			for (int i = Math.min(64, display.size()) - 1; i >= 0; i--) {
				Light l = display.get(i);
				glUniform3f(glGetUniformLocation(id, "lights[" + i + "].position"), l.x, l.y, l.z);
				glUniform4f(glGetUniformLocation(id, "lights[" + i + "].color"), l.r, l.g, l.b, l.a);
				glUniform1f(glGetUniformLocation(id, "lights[" + i + "].radius"), l.radius);
			}
		} else {
			lightShader.unbind();
		}
	}
}
