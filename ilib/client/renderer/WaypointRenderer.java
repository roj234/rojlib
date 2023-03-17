package ilib.client.renderer;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.client.RenderUtils;
import org.lwjgl.opengl.GL11;
import roj.collect.MyHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class WaypointRenderer {
	static final ResourceLocation BEAM = new ResourceLocation("textures/entity/beacon_beam.png");

	public static final int MIN_RENDER_DISTANCE = 1;
	public static final int MAX_RENDER_DISTANCE = -1;

	public static final int MAX_OFFSET_DEGREES = 5;
	public static final int FADING_DISTANCE = 6;

	public static final double FONT_SIZE = 0.005d;
	public static final double MIN_SHOW_DISTANCE = 12d;

	public static final MyHashMap<String, Waypoint> store = new MyHashMap<>();

	public static class Waypoint {
		protected String name;
		protected Vec3d position;
		protected Integer dimension;
		protected int color;

		public Waypoint(String name, Vec3d pos, Integer dimension, int color) {
			this.name = name;
			this.position = pos;
			this.dimension = dimension;
			this.color = color;
		}

		public Integer getDimension() {
			return this.dimension;
		}

		public Vec3d getPosition() {
			return this.position;
		}

		public String getName() {
			return this.name;
		}

		public int getColor() {
			return this.color;
		}

		public boolean renderDistance() {
			return false;
		}

		public float getScale() {
			return 2;
		}
	}

	public static void reset() {
		store.clear();
	}

	public static void addIfNotPresent(String name, String display, int x, int y, int z, Integer dim, int color) {
		store.putIfAbsent(name, new Waypoint(display, new Vec3d(x, y, z), dim, color));
	}

	public static void add(String name, String display, int x, int y, int z, Integer dim, int color) {
		store.put(name, new Waypoint(display, new Vec3d(x, y, z), dim, color));
	}

	public static void remove(String name) {
		store.remove(name);
	}

	public static void render() {
		int dim = ClientProxy.mc.player.dimension;
		for (Waypoint wp : store.values()) {
			Integer i = wp.getDimension();
			if (i == null || i == dim) {
				try {
					doRender(wp);
				} catch (Throwable t) {
					ImpLib.logger().error("During rendering Waypoint(s)");
					t.printStackTrace();
				}
			}
		}
	}

	static void doRender(Waypoint waypoint) {
		RenderManager man = RenderUtils.RENDER_MANAGER;
		if (man.renderViewEntity == null) return;

		Vec3d pPos = man.renderViewEntity.getPositionVector();
		Vec3d pos = waypoint.getPosition();
		double actualDistance = pPos.distanceTo(pos);

		if (MAX_RENDER_DISTANCE > 0 && actualDistance > MAX_RENDER_DISTANCE) return;

		float fadeAlpha = 1.0F;
		if (MIN_RENDER_DISTANCE > 0) {
			if (actualDistance <= MIN_RENDER_DISTANCE) return;

			if (FADING_DISTANCE > 0 && actualDistance <= MIN_RENDER_DISTANCE + FADING_DISTANCE) fadeAlpha = (float) ((actualDistance - MIN_RENDER_DISTANCE) / FADING_DISTANCE);
			if (fadeAlpha < .02f) return;
		}

		double viewDist = actualDistance;
		double maxDist = (ClientProxy.mc.gameSettings.renderDistanceChunks << 4);
		if (viewDist > maxDist) {
			Vec3d delta = pos.subtract(pPos).normalize();
			pos = pPos.add(delta.x * maxDist, delta.y * maxDist, delta.z * maxDist);
			viewDist = maxDist;
		}

		if (viewDist > MIN_SHOW_DISTANCE) {
			double yaw = Math.atan2(man.viewerPosZ - pos.z, man.viewerPosX - pos.x);
			float degrees = (float) Math.toDegrees(yaw) + 90;
			if (degrees < 0) degrees += 360;
			float playerYaw = (man.renderViewEntity.getRotationYawHead() % 360.0F);
			if (playerYaw < 0) playerYaw += 360;

			if (Math.abs(degrees - playerYaw) > MAX_OFFSET_DEGREES) return;
		}

		double shiftX = pos.x - man.viewerPosX;
		double shiftY = pos.y - man.viewerPosY;
		double shiftZ = pos.z - man.viewerPosZ;

		GlStateManager.pushMatrix();
		GlStateManager.enableDepth();
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

		renderBeam(shiftX, -man.viewerPosY, shiftZ, waypoint.getColor(), fadeAlpha);

		float scale = (float) (FONT_SIZE * (0.6 * viewDist + waypoint.getScale()));
		String label = waypoint.getName();

		String dist;
		if (waypoint.renderDistance()) {
			dist = String.valueOf(actualDistance);
			StringBuilder sb = new StringBuilder();
			dist = sb.append("( ").append(dist, 0, dist.indexOf('.') + 2).append("米 )").toString();
		} else {
			dist = null;
		}

		GlStateManager.disableDepth();

		GlStateManager.translate(shiftX + 0.5, shiftY + 4, shiftZ + 0.5);
		RenderUtils.rotateToPlayer();
		GlStateManager.scale(-scale, -scale, scale);

		FontRenderer fr = ClientProxy.mc.fontRenderer;

		double lineHeight = fr.FONT_HEIGHT + (fr.getUnicodeFlag() ? 0 : 4);
		double labelWidth = fr.getStringWidth(label);
		double distWidth = dist == null ? 0 : fr.getStringWidth(dist);

		double maxWidth = Math.max(labelWidth, distWidth);
		// 居中
		// original: y=0 .....  maxWidth + 0 ..... 2.4
		RenderUtils.drawRectangle(-maxWidth * 0.5, 0.5, 0, maxWidth + 0.1, (dist == null ? 1.2 : 2.2) * lineHeight, 0x111111 | (((int) (fadeAlpha * 127)) << 24));

		GlStateManager.translate(labelWidth - Math.floor(labelWidth), 2, 0);
		drawLabel(label, labelWidth, waypoint.getColor() | (int) (fadeAlpha * 255) << 24);

		if (dist != null) {
			GlStateManager.translate(distWidth - Math.floor(distWidth), lineHeight + 1, 0);
			drawLabel(dist, distWidth, 0xffffff | (int) (fadeAlpha * 255) << 24);
		}

		GlStateManager.popMatrix();
		GlStateManager.enableDepth();
		GlStateManager.disableBlend();
	}

	public static void drawLabel(String text, double bgWidth, int color) {
		float textX = (float) (-bgWidth / 2.0D);
		ClientProxy.mc.fontRenderer.drawString(text, textX, 0, color, false);
	}

	static void renderBeam(double x, double y, double z, int color, float alpha) {
		RenderUtils.bindTexture(BEAM);

		int rgba = color | ((int) (alpha * 0.8f * 255) << 24);

		float time = ClientProxy.mc.isGamePaused() ? Minecraft.getSystemTime() / 50L : ClientProxy.mc.world.getTotalWorldTime();
		float texOffset = -(-time * 0.2F - MathHelper.floor(-time * 0.1F)) * 0.6F;

		double dy = (256 * alpha);
		double v2 = (-1 + texOffset);
		double v = (25 * alpha) + v2;

		double b = 0.8;
		double c = 0.2;

		RenderUtils.BUILDER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

		v(x + c, y + dy, z + c, 1, v, rgba);
		v(x + c, y, z + c, 1, v2, rgba);
		v(x + b, y, z + c, 0.0D, v2, rgba);
		v(x + b, y + dy, z + c, 0.0D, v, rgba);
		v(x + b, y + dy, z + b, 1, v, rgba);
		v(x + b, y, z + b, 1, v2, rgba);
		v(x + c, y, z + b, 0.0D, v2, rgba);
		v(x + c, y + dy, z + b, 0.0D, v, rgba);
		v(x + b, y + dy, z + c, 1, v, rgba);
		v(x + b, y, z + c, 1, v2, rgba);
		v(x + b, y, z + b, 0.0D, v2, rgba);
		v(x + b, y + dy, z + b, 0.0D, v, rgba);
		v(x + c, y + dy, z + b, 1, v, rgba);
		v(x + c, y, z + b, 1, v2, rgba);
		v(x + c, y, z + c, 0.0D, v2, rgba);
		v(x + c, y + dy, z + c, 0.0D, v, rgba);

		RenderUtils.TESSELLATOR.draw();
	}

	private static void v(double x, double y, double z, double u, double v, int rgba) {
		RenderUtils.BUILDER.pos(x, y, z).tex(u, v).color(rgba >>> 24 & 255, rgba >>> 16 & 255, rgba >>> 8 & 255, rgba >>> 24 & 255);
	}
}
