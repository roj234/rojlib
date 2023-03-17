package ilib.asm.util;

import ilib.ImpLib;
import ilib.client.RenderUtils;
import ilib.gui.GuiHelper;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;
import roj.reflect.EnumHelper;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Random;

public class XiBaoHelper {
	private static final ResourceLocation XIBAO_TEXTURE = new ResourceLocation(ImpLib.MODID, "textures/gui/xibao.png");
	private static final SoundEvent XIBAO_CRASH_SOUND = new SoundEvent(new ResourceLocation(ImpLib.MODID, "crash"));
	private static final SoundEvent XIBAO_DISCONNECT_SOUND = new SoundEvent(new ResourceLocation(ImpLib.MODID, "disconnect"));
	public static final MusicTicker.MusicType XIBAO = new EnumHelper<>(MusicTicker.MusicType.class)
		.setValueName(null)
		.make("XIBAO", 7, new Class<?>[] {SoundEvent.class, int.class, int.class}, new Object[] {XIBAO_CRASH_SOUND, 200000000, 200000000});

	private static ISound playing;

	@SubscribeEvent
	public static void onGuiOpen(GuiOpenEvent event) {
		if (event.getGui() != null) {
			String name = event.getGui().getClass().getName().toLowerCase();
			if (name.contains("vanillafix") || name.contains("loliasm")) {
				playSingleSound(XIBAO_CRASH_SOUND);
				return;
			} else if (event.getGui() instanceof GuiDisconnected) {
                /*String message = ((GuiDisconnected) event.getGui()).reason;
                if (message.contains("anned") || message.contains("封")) {
                    return;
                }*/
				playSingleSound(XIBAO_DISCONNECT_SOUND);
				return;
			}
		}

		playSingleSound(null);
		snows.clear();
	}

	public static void playSingleSound(SoundEvent sound) {
		if (playing != null) {
			GuiHelper.stopSound(playing);
		}

		if (sound != null) {
			playing = GuiHelper.playMasterSound(sound, 1);
		} else {
			playing = null;
		}
	}

	public static void drawXiBao(int width, int height) {
		GlStateManager.disableLighting();
		GlStateManager.disableFog();
		GlStateManager.color(1, 1, 1, 1);

		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder bb = tessellator.getBuffer();
		RenderUtils.bindTexture(XIBAO_TEXTURE);

		bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		bb.pos(0.0, height, 0).tex(0, 1).endVertex();
		bb.pos(width, height, 0).tex(1, 1).endVertex();
		bb.pos(width, 0, 0).tex(1, 0).endVertex();
		bb.pos(0.0, 0.0, 0.0).tex(0, 0).endVertex();
		tessellator.draw();

		tickSnow(width, height);
	}

	private static final List<Snow> snows = new SimpleList<>(100);
	private static final Random rand = new Random();
	private static final int SNOW_SIZE_PX = 4;

	static final class Snow {
		int size;
		int rgb;
		float x, y, vx, vy;

		Snow(Random r, int guiWidth) {
			x = rand.nextFloat() * guiWidth;
			y = -SNOW_SIZE_PX - rand.nextFloat() * 5;
			vx = (float) (rand.nextGaussian() - 0.5);
			vy = r.nextFloat() * 2;
			size = rand.nextInt(SNOW_SIZE_PX) + 2;
			rgb = 0xFFFFFF;
		}

		boolean tick(BufferBuilder bb, int guiWidth, int guiHeight) {
			if (y >= SNOW_SIZE_PX + guiHeight) return true;
			y += vy;
			x += vx;
			if (x >= guiWidth || x < 0) {
				vx = -vx;
			}
			renderColorQuad(bb, (int) x, (int) y, size, size, rgb);
			return false;
		}
	}

	private static void tickSnow(int width, int height) {
		if (snows.size() < 150) {
			if (rand.nextFloat() > 0.5) {
				for (int i = rand.nextInt(10) - 1; i >= 0; i--) {
					snows.add(new Snow(rand, width));
				}
			}
		}

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder bb = tess.getBuffer();
		bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

		for (int i = snows.size() - 1; i >= 0; i--) {
			Snow snow = snows.get(i);
			if (snow.tick(bb, width, height)) {
				snows.remove(i);
			}
		}

		tess.draw();
	}

	public static void renderColorQuad(BufferBuilder bb, int x, int y, int width, int height, int rgb) {
		bb.pos(0.0D + x, height + y, 0.0D).color(rgb >> 16, rgb >> 8, rgb, 255).endVertex();
		bb.pos(width + x, height + y, 0.0D).color(rgb >> 16, rgb >> 8, rgb, 255).endVertex();
		bb.pos(width + x, 0.0D + y, 0.0D).color(rgb >> 16, rgb >> 8, rgb, 255).endVertex();
		bb.pos(0.0D + x, 0.0D + y, 0.0D).color(rgb >> 16, rgb >> 8, rgb, 255).endVertex();
	}

	public static boolean isXibao() {
		return playing != null;
	}
}
