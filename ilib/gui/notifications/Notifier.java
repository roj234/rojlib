package ilib.gui.notifications;

import roj.collect.SimpleList;

import net.minecraft.client.renderer.GlStateManager;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Notifier {
	static final SimpleList<Notification> activated = new SimpleList<>();

	public static void sendNotification(Notification n) {
		if (!activated.contains(n)) activated.add(n);
	}

	@SubscribeEvent
	public static void tick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.START) return;

		for (int i = activated.size() - 1; i >= 0; i--) {
			Notification n = activated.get(i);
			if (--n.life <= 0) {
				n.onRemove();
				activated.remove(i);
			}
		}
	}

	@SubscribeEvent
	public static void render(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START) return;

		GlStateManager.pushMatrix();
		for (int i = 0; i < activated.size(); i++) {
			Notification n = activated.get(i);
			if (i > 0 && n.isExclusive()) break;
			n.draw();
			GlStateManager.translate(0, n.height, 0);
			if (n.isExclusive()) break;
		}
		GlStateManager.popMatrix();
	}

	public static void postInit() {
		MinecraftForge.EVENT_BUS.register(Notifier.class);
	}
}
