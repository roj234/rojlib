package ilib.client.renderer;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;

import java.util.Iterator;

/**
 * @author solo6975
 * @since 2022/4/2 20:38
 */
public class DebugRenderer {
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

        for (int z = 0; z < 16; z ++) {
            for (int x = 0; x < 16; x ++) {
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

        BufferBuilder bb = RenderUtils.BUILDER;

        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        EntityPlayer p = ClientProxy.mc.player;
        Iterator<BlockPos.MutableBlockPos> itr = BlockPos.getAllInBoxMutable(new BlockPos(p.posX-24, 0, p.posZ-24),
                                                                             new BlockPos(p.posX+24, 0, p.posZ+24)).iterator();

        World w = ClientProxy.mc.world;
        while(itr.hasNext()) {
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
}
