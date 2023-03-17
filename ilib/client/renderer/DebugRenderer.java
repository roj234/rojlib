package ilib.client.renderer;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.util.EntityHelper;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;
import roj.math.Vec3d;
import roj.opengl.render.ArenaRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Iterator;
import java.util.Random;

/**
 * @author solo6975
 * @since 2022/4/2 20:38
 */
public class DebugRenderer {
	private static final SimpleList<AxisAlignedBB> nextDraw = new SimpleList<>();
	private static final SimpleList<BlockPos[]> nextLine = new SimpleList<>();

	public static void clearMatrixStack() {
		while (0 != GL11.glGetError()) ;
		while (0 == GL11.glGetError()) GL11.glPopMatrix();
	}

	public static void lineTo(BlockPos pos, BlockPos pos1) {
		nextLine.add(new BlockPos[] {pos, pos1});
	}

	public static void region(BlockPos pos) {
		if (pos == null) {nextDraw.clear();} else nextDraw.add(new AxisAlignedBB(pos, pos.add(1, 1, 1)));
	}

	public static void region(AxisAlignedBB box) {
		nextDraw.add(box);
	}

	public static void drawPending() {
		GlStateManager.disableDepth();
		GlStateManager.disableTexture2D();
		Random r = new Random(0);
		for (int i = 0; i < nextDraw.size(); i++) {
			AxisAlignedBB box = nextDraw.get(i);
			if (EntityHelper.canPlayerSee(box)) {
				r.setSeed(box.hashCode());
				ArenaRenderer.INSTANCE.setColor(0xFF000000 | r.nextInt());
				// noinspection all
				ArenaRenderer.INSTANCE.render(new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ), (System.currentTimeMillis() / 20) % 36000);
			}
		}
		GlStateManager.color(1, 1, 1, 1);
		GL11.glLineWidth(5);
		GL11.glBegin(GL11.GL_LINES);
		for (int i = 0; i < nextLine.size(); i++) {
			BlockPos[] box = nextLine.get(i);
			GL11.glVertex3i(box[0].getX(), box[0].getY(), box[0].getZ());
			GL11.glVertex3i(box[1].getX(), box[1].getY(), box[1].getZ());
		}
		GL11.glEnd();
		nextDraw.clear();
		nextLine.clear();
		GlStateManager.enableDepth();
		GlStateManager.enableTexture2D();
	}

	public static void drawChunk() {
		EntityPlayer p = ClientProxy.mc.player;

		double y = (int) p.posY & ~0xF;
		y -= 16;
		double sX = p.chunkCoordX << 4;
		double sZ = p.chunkCoordZ << 4;

		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();

		GlStateManager.glLineWidth(1);

		BufferBuilder bb = RenderUtils.BUILDER;
		bb.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);

		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				bb.pos(sX + x, y, sZ + z).endVertex();
				bb.pos(sX + x, y, sZ + z).endVertex();
				bb.pos(sX + x, y, sZ + 1.0D + z).endVertex();
				bb.pos(sX + x + 1.0D, y, sZ + 1.0D + z).endVertex();
			}
		}

		GlStateManager.color(0, 1, 1, 1);
		RenderUtils.TESSELLATOR.draw();

		bb.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);

		int dist = ClientProxy.mc.gameSettings.renderDistanceChunks;

		for (int z = -16 * dist; z <= 16 * dist; z += 16) {
			for (int x = -16 * dist; x <= 16 * dist; x += 16) {
				bb.pos(sX + x, y, sZ + z).endVertex();
				bb.pos(sX + x, y, sZ + z).endVertex();
				bb.pos(sX + x, y, sZ + 16.0D + z).endVertex();
				bb.pos(sX + x + 16.0D, y, sZ + 16.0D + z).endVertex();
			}
		}

		GlStateManager.color(0.25F, 0.25F, 1, 1);
		RenderUtils.TESSELLATOR.draw();

		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
	}

	public static void drawLight(boolean doAir, boolean doSafe) {
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();

		BufferBuilder bb = RenderUtils.BUILDER;

		bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

		EntityPlayer p = ClientProxy.mc.player;
		Iterator<BlockPos.MutableBlockPos> itr = BlockPos.getAllInBoxMutable(new BlockPos(p.posX - 24, 0, p.posZ - 24), new BlockPos(p.posX + 24, 0, p.posZ + 24)).iterator();

		World w = ClientProxy.mc.world;
		while (itr.hasNext()) {
			BlockPos.MutableBlockPos pos = itr.next();
			int x = pos.getX();
			int z = pos.getZ();

			Chunk c = w.getChunk(pos);
			int y = (int) (p.posY + 0.5);

			IBlockState state;
			do {
				pos.setY(y--);

				state = c.getBlockState(pos);
			} while (!state.isSideSolid(w, pos, EnumFacing.UP));
			pos.setY(y += 2);

			float bl = w.getLightFor(EnumSkyBlock.BLOCK, pos) / 15f;
			float sl = w.getLightFor(EnumSkyBlock.SKY, pos) / 15f;

			if (bl < 0.5f) {
				float g = sl > 0.5f ? 1 : 0;
				bb.pos(x + 0.25F, y + 0.005D, z + 0.75F).color(1, g, 0, 0.5F).endVertex();
				bb.pos(x + 0.75F, y + 0.005D, z + 0.75F).color(1, g, 0, 0.5F).endVertex();
				bb.pos(x + 0.75F, y + 0.005D, z + 0.25F).color(1, g, 0, 0.5F).endVertex();
				bb.pos(x + 0.25F, y + 0.005D, z + 0.25F).color(1, g, 0, 0.5F).endVertex();
			}

			pos.setY(0);
		}

		RenderUtils.TESSELLATOR.draw();

		GlStateManager.enableTexture2D();
	}

	public static void displayIfHover(TileEntity tile, String msg) {
		RayTraceResult obj = ClientProxy.mc.objectMouseOver;
		if (obj == null || obj.typeOfHit != RayTraceResult.Type.BLOCK || !obj.getBlockPos().equals(tile.getPos())) return;

		ClientProxy.mc.ingameGUI.setOverlayMessage(msg, false);
	}
}
