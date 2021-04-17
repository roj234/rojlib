package ilib.asm.nx.client;

import ilib.client.event.RenderStageEvent;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.profiler.Profiler;

import net.minecraftforge.common.MinecraftForge;

/**
 * @author Roj233
 * @since 2022/5/17 4:05
 */
@Nixim("/")
class GlobalShaderHook extends EntityRenderer {
	@Copy(unique = true)
	private static int stage;

	GlobalShaderHook() {
		super(null, null);
	}

	@Inject(value = "/", at = Inject.At.INVOKE, param = {"func_76318_c", "redirect__endStartSection"})
	public void renderWorldPass(int pass, float partialTicks, long finishTimeNano) {}

	@Copy(unique = true)
	private static void redirect__endStartSection(Profiler profiler, String section) {
		profiler.endStartSection(section);
		switch (section) {
			case "terrain":
				MinecraftForge.EVENT_BUS.post(new RenderStageEvent(RenderStageEvent.Stage.OPAQUE));
				stage = 1;
				break;
			case "translucent":
				MinecraftForge.EVENT_BUS.post(new RenderStageEvent(RenderStageEvent.Stage.TRANSLUCENT));
				stage = 2;
				break;
			case "entities":
			case "outline":
			case "destroyProgress":
				switch (stage) {
					case 1:
						MinecraftForge.EVENT_BUS.post(new RenderStageEvent(RenderStageEvent.Stage.OPAQUE_END));
						break;
					case 2:
						MinecraftForge.EVENT_BUS.post(new RenderStageEvent(RenderStageEvent.Stage.TRANSLUCENT_END));
						break;
				}
				stage = 0;
				break;
		}
	}
}
