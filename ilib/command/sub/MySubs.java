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

import ilib.Config;
import ilib.ImpLib;
import ilib.asm.Transformer;
import ilib.client.TextureHelper;
import ilib.util.Colors;
import ilib.util.DimensionHelper;
import ilib.util.ItemUtils;
import ilib.util.PlayerUtil;
import ilib.util.internal.ItemSpecialRenderer;
import roj.collect.LongMap;
import roj.collect.MyHashSet;
import roj.opengl.util.OpenGLDebug;
import roj.text.TextUtil;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;

import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/5/23 14:10
 */
public abstract class MySubs extends AbstractSubCommand {
    static LongMap<Object> generatedChunks;

    public static final MySubs CLEAR = new MySubs("clear_item") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            // world/* mob/entity/item
            server.getCommandManager().executeCommand(sender, "kill @e[type=item]");
        }
    };

    public static final MySubs REGEN = new MySubs("regenLoadedChunks") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (generatedChunks == null)
                generatedChunks = new LongMap<>();

            World world = sender.getEntityWorld();

            int i = 0;

            final ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
            final IChunkGenerator generator = provider.chunkGenerator;
            for (Chunk chunk : provider.getLoadedChunks()) {
                if (generatedChunks.put(((long) chunk.x << 32) | chunk.z, true) == Boolean.TRUE)
                    continue;
                generator.populate(chunk.x, chunk.z);
                GameRegistry.generateWorld(chunk.x, chunk.z, world, generator, provider);
                chunk.markDirty();
                i++;
            }
            PlayerUtil.sendTo(sender, Colors.GREY + "成功重生了 " + Colors.ORANGE + i + Colors.GREY + " 个区块");
        }
    };

    public static final MySubs GC = new MySubs("gc") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            Runtime r = Runtime.getRuntime();
            long before = r.totalMemory() - r.freeMemory();
            System.gc();
            long after = r.totalMemory() - r.freeMemory();
            PlayerUtil.sendTo(sender, Colors.GREY + "成功释放了 " + Colors.ORANGE + TextUtil.scaledNumber(before - after) + 'B' + Colors.GREY + " 内存");
        }
    };

    public static final MySubs TPS_CHANGE = new MySubs("tps") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (!Config.enableTPSChange) {
                throw new WrongUsageException("This command is not enabled yet.");
            }
            if (args.length == 0)
                throw new WrongUsageException("/il tps <tps>");
            if (TextUtil.isNumber(args[0]) != 0)
                throw new WrongUsageException("/il tps <tps>");
            int i = Integer.parseInt(args[0]);
            if (i <= 0 || i > 500) {
                throw new WrongUsageException("Tps must in [1, 500]");
            }
            Transformer.MSpT = 1000 / i;
        }
    };

    public static final MySubs UNLOAD_CHUNKS = new MySubs("chunks") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length < 1) {
                throw new WrongUsageException("/il chunks <view/clear>");
            }
            switch (args[0]) {
                case "clear": {
                    int i = 0;
                    Set<String> set = new MyHashSet<>();
                    for (WorldServer w : DimensionManager.getWorlds()) {
                        if (w.playerEntities.isEmpty()) {
                            DimensionManager.unloadWorld(DimensionHelper.idFor(w));
                            set.add(String.valueOf(DimensionHelper.idFor(w)));
                        } else if (w.getChunkProvider() != null) {
                            ChunkProviderServer provider = w.getChunkProvider();

                            PlayerChunkMap map = w.getPlayerChunkMap();
                            LongMap<Boolean> longs = new LongMap<>();
                            for (Iterator<Chunk> itr = map.getChunkIterator(); itr.hasNext(); ) {
                                Chunk chunk = itr.next();
                                longs.put((long) chunk.x << 32 | (long) chunk.z, null);
                            }

                            for (Chunk chunk : provider.getLoadedChunks()) {
                                if (!longs.containsKey((long) chunk.x << 32 | (long) chunk.z)) {
                                    provider.queueUnload(chunk);
                                    i++;
                                }
                            }
                        } else {
                            PlayerUtil.sendTo(sender, Colors.GREY + "维度ID " + Colors.ORANGE + DimensionHelper.idFor(w) + Colors.GREY + " 不支持卸载");
                        }
                    }
                    PlayerUtil.sendTo(sender, Colors.GREY + "成功卸载 " + Colors.ORANGE + i + Colors.GREY + " 个区块");
                    if (!set.isEmpty())
                        PlayerUtil.sendTo(sender, Colors.GREY + "成功卸载 " + Colors.ORANGE + set + Colors.GREY + " 世界");
                }
                break;
                case "view": {
                    for (WorldServer w : DimensionManager.getWorlds()) {
                        String s = "未知";
                        if (w.getChunkProvider() != null) {
                            s = String.valueOf(w.getChunkProvider().getLoadedChunks().size());
                        }
                        PlayerUtil.sendTo(sender, Colors.GREY + "维度ID " + Colors.ORANGE + DimensionHelper.idFor(w) + Colors.GREY + " 加载了 " + Colors.ORANGE + s + Colors.GREY + " 个区块");
                    }
                }
                break;
                default:
            }
        }
    };

    public static final MySubs CHECK_OD = new MySubs("itemod") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length < 1) {
                throw new WrongUsageException("/il itemod <oredict>");
            }

            List<ItemStack> stacks = OreDictionary.getOres(args[0], false);
            sender.sendMessage(new TextComponentString("\u00a7a  矿物词典 " + args[0] + " 查询到的物品(" + stacks.size() + ')'));
            for (ItemStack stack : stacks) {
                sender.sendMessage(new TextComponentString("\u00a77 - " + stack.getItem().getItemStackDisplayName(stack) + "(" + ItemUtils.stack2String(stack) + ")"));
            }
        }
    };
    public static final MySubs RENDER_INFO = new MySubs("info") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            ItemSpecialRenderer.onRender();
        }
    };
    public static final MySubs TILE_TEST = new MySubs("test") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0)
                ilib.util.internal.T3SpecialRenderer.release();
            else {
                int index = args[0].indexOf("B") - 1;
                if (index < 0)
                    return;
                String b = args[0].substring(index);
                String i = args[0].substring(0, index - 1);
                ilib.util.internal.T3SpecialRenderer.render(b, i);
            }
        }
    };

    public static MySubs DUMP_GL_INFO;
    public static MySubs RELOAD_TEXTURE;

    static {
        if (ImpLib.isClient)
            clientInit();
    }

    @SideOnly(Side.CLIENT)
    public static void clientInit() {
        DUMP_GL_INFO = new MySubs("dump_gl_info") {
            public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                for (String s : OpenGLDebug.dumpStrings())
                    ItemSpecialRenderer.b(s);
            }
        };

        RELOAD_TEXTURE = new MySubs("reloadTex") {
            public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                TextureHelper.manualReload();
                sender.sendMessage(new TextComponentString("材质已经重新加载."));
            }
        };
    }

    private final String name, help;

    public MySubs(String name) {
        this.name = name;
        this.help = null;
    }

    public MySubs(String name, String help) {
        this.name = name;
        this.help = help;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHelp() {
        return this.help == null ? super.getHelp() : this.help;
    }

    @Override
    public abstract void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;
}
