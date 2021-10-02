/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ilib.command.sub;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.client.util.RenderUtils;
import ilib.math.Arena;
import ilib.math.Section;
import ilib.misc.SelectionCache;
import ilib.util.BlockHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import roj.collect.ToIntMap;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/24 0:25
 */
public class CommandPixelPainting extends AbstractSubCommand {
    private static final int BUFFER = OpenGlHelper.glGenFramebuffers();

    @Override
    public String getName() {
        return "pixel_painting";
    }

    @Override
    public String getHelp() {
        return "command.il.help.pixel_painting";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        World w = sender.getEntityWorld();

        EntityPlayer player = getAsPlayer(sender);

        switch (args.length) {
            case 1: {
                Arena arena = SelectionCache.get(player.getUniqueID().getMostSignificantBits());
                if (arena == null) {
                    throw new CommandException("command.ilib.sel.arena_not");
                }
                if (arena.isOK()) {
                    Section sect = arena.toSection();

                    if (sect.ymax != sect.ymin)
                        throw new CommandException("command.ilib.sel.not_same_y");

                    ImpLib.proxy.runAtMainThread(true, () -> savePixelPainting(w, args[0], sect.minBlock(), sect.maxBlock()));
                } else {
                    throw new CommandException("command.ilib.sel.arena_not");
                }
            }
            break;
            case 6: {
                // sx sy ex sz ex file
                try {
                    int y;
                    Section section = new Section(Integer.parseInt(args[0]), y = Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), y, Integer.parseInt(args[4]));

                    ImpLib.proxy.runAtMainThread(true, () -> savePixelPainting(w, args[0], section.minBlock(), section.maxBlock()));
                } catch (NumberFormatException e) {
                    throw new CommandException("command.ilib.int", "argument 1-5");
                }

            }
            break;
            default:
                throw new WrongUsageException("/il pxp <file/x> [y] [z] [x2] [z2] [file]");
        }

        notifyCommandListener(sender, "command.il.ok");
    }

    private static void savePixelPainting(World world, String fileName, BlockPos startPoint, BlockPos pos1) {
        int y = startPoint.getY();

        int startX = startPoint.getX();
        int startZ = startPoint.getZ();

        int width = Math.abs(pos1.getX() - startPoint.getX()) + 1;
        int height = Math.abs(pos1.getZ() - startPoint.getZ()) + 1;

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] rawArray = ((DataBufferInt) output.getRaster().getDataBuffer()).getData();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                rawArray[w * height + h] = getBlockIntColorAt(world, pos.setPos(startX + w, BlockHelper.getTopBlockY(world, startX + w, startZ + h), startZ + h));
            }
        }
        output.flush();

        try {

            ImageIO.write(output, "png", new File("./" + fileName.replace("/", "_").replace("\\", "_")));
        } catch (IOException e) {
            ImpLib.logger().warn("Fatal error occurred during pixel image saving");
            ImpLib.logger().catching(e);
        }
    }

    static ToIntMap<IBlockState> modelColorCache = new ToIntMap<>();

    static BlockRendererDispatcher dispatcher = ClientProxy.mc.getBlockRendererDispatcher();

    private static int getBlockIntColorAt(World world, BlockPos.MutableBlockPos pos) {
        IBlockState state = world.getBlockState(pos).getActualState(world, pos);
        switch (state.getRenderType()) {
            case INVISIBLE:
                return 0x00000000;
            case ENTITYBLOCK_ANIMATED:
                return 0xFF00FF00;
            case LIQUID:
                return 0xFFEEEEFF;
            case MODEL: {
                if (modelColorCache.containsKey(state))
                    return modelColorCache.getInt(state);
                else {
                    IBakedModel model = dispatcher.getModelForState(state);
                    state = state.getBlock().getExtendedState(state, world, pos);
                    int color = getComputedRenderResult(world, model, state, pos, MathHelper.getPositionRandom(pos));
                    modelColorCache.putInt(state, color);
                    return color;
                }
            }
        }
        throw new IllegalStateException(String.valueOf(state.getRenderType()));
    }

    static ByteBuffer pixelDataBuffer = ByteBuffer.allocateDirect(4);
    static IntBuffer buffer = pixelDataBuffer.asIntBuffer();

    private static int getComputedRenderResult(World world, IBakedModel model, IBlockState state, BlockPos pos, long rand) {
        TextureAtlasSprite sprite = model.getParticleTexture();

        int x = 0;
        int y = 0;
        int w = 1;
        int h = 1;

        float minU = sprite.getMinU(),
                u = minU + (sprite.getMaxU() - minU) * w / 16,
                minV = sprite.getMinV(),
                v = minV + (sprite.getMaxV() - minV) * h / 16;

        //OpenGlHelper.glBindFramebuffer(GL30.GL_FRAMEBUFFER, BUFFER);

        RenderUtils.bindMinecraftBlockSheet();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        buffer.pos(x, y + h, 0).tex(minU, v).endVertex();
        buffer.pos(x + w, y + h, 0).tex(u, v).endVertex();
        buffer.pos(x + w, y, 0).tex(u, minV).endVertex();
        buffer.pos(x, y, 0).tex(minU, minV).endVertex();

        tessellator.draw();

        pixelDataBuffer.clear();
        GL11.glReadPixels(x, y, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixelDataBuffer);

        //OpenGlHelper.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        return CommandPixelPainting.buffer.get(0);
    }

    @Override
    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        switch (args.length) {
            case 2:
            case 3:
            case 4:
                return CommandBase.getTabCompletionCoordinate(args, 1, pos);
            case 5:
            case 6:
                return CommandBase.getTabCompletionCoordinate(args.length == 6 ? Arrays.copyOf(args, 7) : args, 3, pos);
        }
        return empty_list;
    }
}
