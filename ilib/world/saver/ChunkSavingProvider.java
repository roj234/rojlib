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

package ilib.world.saver;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.util.PlayerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ChunkSavingProvider extends ChunkProviderClient {
    private final Chunk blankChunk;
    private final Long2ObjectMap<Chunk> loadedChunks = new Long2ObjectOpenHashMap<Chunk>(1024) {
        public static final long serialVersionUID = 1;

        protected void rehash(int i) {
            if (i > this.key.length) {
                super.rehash(i);
            }
        }
    };
    private final World world;
    private final AnvilChunkLoader loader;

    protected static final DataFixer DEAFULTFIXER = new DataFixer(1343);

    public ChunkSavingProvider(World world, File chunkSavingloc) {
        super(world);
        this.blankChunk = new EmptyChunk(world, 0, 0);
        this.world = world;
        this.loader = new AnvilChunkLoader(chunkSavingloc, DEAFULTFIXER);
    }

    @Override
    public void unloadChunk(int x, int z) {
        Chunk chunk = this.provideChunk(x, z);
        if (!chunk.isEmpty()) {
            chunk.onUnload();
        }

        this.loadedChunks.remove(ChunkPos.asLong(x, z));
    }

    @Nullable
    @Override
    public Chunk getLoadedChunk(int x, int z) {
        return this.loadedChunks.get(ChunkPos.asLong(x, z));
    }

    @Nonnull
    @Override
    public Chunk loadChunk(int x, int z) {
        Chunk chunk = new Chunk(this.world, x, z) {
            @Override
            public void read(@Nonnull PacketBuffer buffer, int i, boolean b) {
                super.read(buffer, i, b);
                ChunkSavingProvider.this.saveChunk(this);
            }

            @Override
            public void onUnload() {
                super.onUnload();
                ChunkSavingProvider.this.saveChunk(this);
            }
        };
        this.loadedChunks.put(ChunkPos.asLong(x, z), chunk);
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk));
        chunk.markLoaded(true);
        return chunk;
    }

    int i = 0;

    public void saveChunk(Chunk chunk) {
        try {
            this.loader.saveChunk(this.world, chunk);
            PlayerUtil.sendTo(null, "[WS]保存 " + chunk.x + "," + chunk.z);
        } catch (Throwable e) {
            PlayerUtil.sendTo(null, "[WS]保存 " + chunk.x + "," + chunk.z + " 时发生了错误! 查看latest.log");
            ImpLib.logger().error(e);
        }
    }

    public int saveWorld() {
        this.loader.writeNextIO();
        final WorldInfo wInfo = world.getWorldInfo();
        wInfo.setBorderSize(world.getWorldBorder().getDiameter());
        wInfo.getBorderCenterX(world.getWorldBorder().getCenterX());
        wInfo.getBorderCenterZ(world.getWorldBorder().getCenterZ());
        wInfo.setBorderSafeZone(world.getWorldBorder().getDamageBuffer());
        wInfo.setBorderDamagePerBlock(world.getWorldBorder().getDamageAmount());
        wInfo.setBorderWarningDistance(world.getWorldBorder().getWarningDistance());
        wInfo.setBorderWarningTime(world.getWorldBorder().getWarningTime());
        wInfo.setBorderLerpTarget(world.getWorldBorder().getTargetSize());
        wInfo.setBorderLerpTime(world.getWorldBorder().getTimeUntilTarget());
        //world.getSaveHandler().saveWorldInfoWithPlayer(world.getWorldInfo(), world.server.getPlayerList().getHostPlayerData());
        world.getSaveHandler().saveWorldInfoWithPlayer(wInfo, null);
        world.mapStorage.saveAllData();

        int saved = 0;
        for (Chunk chunk : this.loadedChunks.values()) {
            if (chunk.needsSaving(true)) {
                try {
                    this.loader.saveChunk(this.world, chunk);
                    chunk.setModified(false);
                    saved++;
                } catch (Throwable e) {
                    ImpLib.logger().error(e);
                }
            }
        }

        return saved;
    }

    @Nonnull
    @Override
    public Chunk provideChunk(int x, int z) {
        return this.loadedChunks.getOrDefault(ChunkPos.asLong(x, z), this.blankChunk);
    }

    long lastTime;

    @Override
    public boolean tick() {
        long i = System.currentTimeMillis();

        if(i - lastTime > 5000) {
            lastTime = i;
            int saved = saveWorld();
            ClientProxy.mc.ingameGUI.setOverlayMessage("WorldSaver: 自动保存完毕(" + saved + ")", true);
        }

        for (Chunk chunk : this.loadedChunks.values()) {
            chunk.onTick(System.currentTimeMillis() - i > 5L);
        }

        if (System.currentTimeMillis() - i > 100L) {
            ImpLib.logger().warn("Clientside chunk ticking took {} ms", System.currentTimeMillis() - i);
        }

        return false;
    }

    @Nonnull
    @Override
    public String makeString() {
        return "[MI]MultiplayerChunkSavingCache: " + this.loadedChunks.size();
    }

    @Override
    public boolean isChunkGeneratedAt(int x, int z) {
        return this.loadedChunks.containsKey(ChunkPos.asLong(x, z));
    }
}
