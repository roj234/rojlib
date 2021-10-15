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
package ilib.asm.nixim;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.passive.EntitySkeletonHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.UUID;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.world.WorldServer")
public abstract class NiximWorldServer extends WorldServer {

    public NiximWorldServer(MinecraftServer server, ISaveHandler saveHandlerIn, WorldInfo info, int dimensionId, Profiler profilerIn) {
        super(server, saveHandlerIn, info, dimensionId, profilerIn);
    }

    @Shadow("field_73063_M")
    private PlayerChunkMap playerChunkMap;

    @Inject("func_147456_g")
    protected void updateBlocks() {
        this.playerCheckLight();
        if (this.worldInfo.getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES) {
            Iterator<Chunk> chunk = this.playerChunkMap.getChunkIterator();

            while (chunk.hasNext()) {
                chunk.next().onTick(false);
            }
        } else {
            boolean rain = this.isRaining();
            boolean thunder = this.isThundering();
            this.profiler.startSection("pollingChunks");

            Iterator<Chunk> iterator = this.getPersistentChunkIterable(this.playerChunkMap.getChunkIterator());
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
            while (iterator.hasNext()) {
                this.profiler.startSection("getChunk");
                Chunk chunk = iterator.next();
                int bx = chunk.x << 4;
                int bz = chunk.z << 4;
                this.profiler.endStartSection("checkNextLight");
                chunk.enqueueRelightChecks();
                this.profiler.endStartSection("tickChunk");
                chunk.onTick(false);
                this.profiler.endStartSection("thunder");
                int j2;
                if (this.provider.canDoLightning(chunk) && rain && thunder && this.rand.nextInt(100000) == 0) {
                    this.updateLCG = this.updateLCG * 3 + 1013904223;
                    j2 = this.updateLCG >> 2;
                    BlockPos pos1 = this.adjustPosToNearbyEntity(pos.setPos(bx + (j2 & 15), 0, bz + (j2 >> 8 & 15)));
                    if (this.isRainingAt(pos1)) {
                        DifficultyInstance diff = this.getDifficultyForLocation(pos1);
                        if (this.getGameRules().getBoolean("doMobSpawning") && this.rand.nextDouble() < (double) diff.getAdditionalDifficulty() * 0.01) {
                            EntitySkeletonHorse trapHorse = new EntitySkeletonHorse(this);
                            trapHorse.setTrap(true);
                            trapHorse.setGrowingAge(0);
                            trapHorse.setPosition(pos1.getX(), pos1.getY(), pos1.getZ());
                            this.spawnEntity(trapHorse);
                            this.addWeatherEffect(new EntityLightningBolt(this, pos1.getX(), pos1.getY(), pos1.getZ(), true));
                        } else {
                            this.addWeatherEffect(new EntityLightningBolt(this, pos1.getX(), pos1.getY(), pos1.getZ(), false));
                        }
                    }
                }

                this.profiler.endStartSection("iceandsnow");
                if (this.provider.canDoRainSnowIce(chunk) && this.rand.nextInt(16) == 0) {
                    this.updateLCG = this.updateLCG * 3 + 1013904223;
                    j2 = this.updateLCG >> 2;
                    BlockPos pos1 = this.getPrecipitationHeight(pos.setPos(bx + (j2 & 15), 0, bz + (j2 >> 8 & 15)));
                    if (this.isAreaLoaded(pos.setPos(pos1.getX(), pos1.getY() - 1, pos1.getZ()), 1) && this.canBlockFreezeNoWater(pos)) {
                        this.setBlockState(pos, Blocks.ICE.getDefaultState());
                    }

                    if (rain) {
                        if (this.canSnowAt(pos1, true)) {
                            this.setBlockState(pos1, Blocks.SNOW_LAYER.getDefaultState());
                        }
                        if (this.getBiome(pos).canRain()) {
                            this.getBlockState(pos).getBlock().fillWithRain(this, pos);
                        }
                    }
                }

                int speed = this.getGameRules().getInt("randomTickSpeed");

                this.profiler.endStartSection("tickBlocks");
                if (speed > 0) {
                    for (ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
                        if (storage != Chunk.NULL_BLOCK_STORAGE && storage.needsRandomTick()) {
                            for (int i = 0; i < speed; ++i) {
                                this.updateLCG = this.updateLCG * 3 + 1013904223;
                                int rnd = this.updateLCG >> 2;
                                int x = rnd & 15;
                                int y = rnd >> 8 & 15;
                                int z = rnd >> 16 & 15;
                                IBlockState state = storage.get(x, z, y);
                                Block block = state.getBlock();
                                this.profiler.startSection("randomTick");
                                if (block.getTickRandomly()) {
                                    block.randomTick(this, pos.setPos(x + bx, z + storage.getYLocation(), y + bz), state, this.rand);
                                }
                                this.profiler.endSection();
                            }
                        }
                    }
                }
                this.profiler.endSection();
            }
            pos.release();

            this.profiler.endSection();
        }

    }

    @Shadow("func_175733_a")
    public Entity getEntityFromUuid(UUID uuid) {
        return null;
    }

    @Nullable
    @Copy
    public EntityPlayer func_152378_a(UUID uuid) {
        return (EntityPlayer) getEntityFromUuid(uuid);
    }

    @Inject("func_184162_i")
    protected void playerCheckLight() {
        this.profiler.startSection("playerCheckLight");
        if (!this.playerEntities.isEmpty()) {
            int i = this.rand.nextInt(this.playerEntities.size());
            EntityPlayer entityplayer = this.playerEntities.get(i);
            int j = MathHelper.floor(entityplayer.posX) + this.rand.nextInt(11) - 5;
            int k = MathHelper.floor(entityplayer.posY) + this.rand.nextInt(11) - 5;
            int l = MathHelper.floor(entityplayer.posZ) + this.rand.nextInt(11) - 5;
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(j, k, l);
            this.checkLight(pos);
            pos.release();
        }

        this.profiler.endSection();
    }

}