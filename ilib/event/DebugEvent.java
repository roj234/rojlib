package ilib.event;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import static ilib.ClientProxy.mc;

/**
 * @author Roj234
 * @since 2022/4/4 12:08
 */
public final class DebugEvent {
	private static boolean enabled;

	public static void init() {
		MinecraftForge.EVENT_BUS.register(DebugEvent.class);
	}

	@SubscribeEvent
	public static void onOpenGui(GuiOpenEvent event) {

	}

	@SubscribeEvent
	public static void onRenderWordLast(RenderWorldLastEvent event) {
		EntityPlayer p = mc.player;

		double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * (double) event.getPartialTicks();
		double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * (double) event.getPartialTicks();
		double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * (double) event.getPartialTicks();

		GlStateManager.pushMatrix();
		GlStateManager.translate(-x, -y, -z);

		GlStateManager.enableCull();
		GlStateManager.popMatrix();
	}

	@SubscribeEvent
	public static void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
		// put overlay render code here
	}

	@SubscribeEvent
	public static void onTick(TickEvent.ClientTickEvent event) {
		// put tick execution code here
	}
}
