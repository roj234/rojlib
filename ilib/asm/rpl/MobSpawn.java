package ilib.asm.rpl;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.WeightedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.List;
import java.util.Random;

/**
 * @author solo6975
 * @since 2022/5/2 21:57
 */
public class MobSpawn {
	private static final int field_180268_a = (int) Math.pow(17.0D, 2.0D);

	private final LongOpenHashSet field_77193_b = new LongOpenHashSet();

	public MobSpawn() {}

	public int func_77192_a(WorldServer w, boolean hostile, boolean peace, boolean animal) {
		if (!hostile && !peace) return 0;

		LongOpenHashSet set = this.field_77193_b;
		set.clear();
		int tries = 0;
		for (int xx = 0; xx < w.playerEntities.size(); xx++) {
			EntityPlayer entityplayer = w.playerEntities.get(xx);
			if (entityplayer.isSpectator()) continue;
			int cx = MathHelper.floor(entityplayer.posX / 16.0D) - 8;
			int cz = MathHelper.floor(entityplayer.posZ / 16.0D) - 8;
			for (int ox = 0; ox < 16; ox++) {
				for (int oz = 0; oz < 16; oz++) {
					if (((ox | oz) & 15) == 0) continue;

					long id = (long) (ox + cx) << 32 | (oz + cz);
					if (!set.contains(id)) {
						++tries;
						ChunkPos chunkpos = new ChunkPos(ox + cx, oz + cz);
						if (w.getWorldBorder().contains(chunkpos)) {
							PlayerChunkMapEntry entry = w.getPlayerChunkMap().getEntry(ox + cx, oz + cz);
							if (entry != null && entry.isSentToPlayers()) {
								set.add(id);
							}
						}
					}
				}
			}
		}

		int spawned = 0;
		BlockPos spawn = w.getSpawnPoint();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (EnumCreatureType type : EnumCreatureType.values()) {
			if ((type.getPeacefulCreature() ? peace : hostile) && (!type.getAnimal() || animal)) {
				int count = w.countEntities(type, true);
				int max = type.getMaxNumberOfCreature() * tries / field_180268_a;
				if (count <= max) {
					label143:
					for (LongIterator itr = set.iterator(); itr.hasNext(); ) {
						long pos1 = itr.nextLong();
						int x, y, z;

						IBlockState state;
						do {
							Chunk chunk = w.getChunk((int) (pos1 >>> 32), (int) pos1);
							x = ((int) (pos1 >>> 32) << 4) + w.rand.nextInt(16);
							z = ((int) pos1 << 4) + w.rand.nextInt(16);
							y = MathHelper.roundUp(chunk.getHeight(pos.setPos(x, 0, z)) + 1, 16);
							y = w.rand.nextInt(y > 0 ? y : chunk.getTopFilledSegment() + 16 - 1);

							state = w.getBlockState(pos.setPos(x, y, z));
						} while (state.isNormalCube());

						int spawned1 = 0;

						for (int i = 0; i < 3; ++i) {
							int x2 = x;
							int y2 = y;
							int z2 = z;
							Biome.SpawnListEntry entry = null;
							IEntityLivingData data = null;

							int tries1 = MathHelper.ceil(Math.random() * 4.0D);
							while (tries1-- > 0) {
								x2 += w.rand.nextInt(6) - w.rand.nextInt(6);
								y2 += w.rand.nextInt(1) - w.rand.nextInt(1);
								z2 += w.rand.nextInt(6) - w.rand.nextInt(6);
								pos.setPos(x2, y2, z2);
								float x1 = x2 + 0.5F;
								float z1 = z2 + 0.5F;
								if (w.isAnyPlayerWithinRangeAt(x1, y2, z1, 24.0D) || spawn.distanceSq(x1, y2, z1) < 576.0D) {
									continue;
								}

								if (entry == null) {
									entry = w.getSpawnListEntryForTypeAt(type, pos);
									if (entry == null) break;
								}

								if (w.canCreatureTypeSpawnHere(type, entry, pos) && func_180267_a(EntitySpawnPlacementRegistry.getPlacementForEntity(entry.entityClass), w, pos)) {
									EntityLiving entity;
									try {
										entity = entry.newInstance(w);
									} catch (Exception e) {
										e.printStackTrace();
										return spawned;
									}

									entity.setLocationAndAngles(x1, y2, z1, w.rand.nextFloat() * 360.0F, 0.0F);
									Event.Result canSpawn = ForgeEventFactory.canEntitySpawn(entity, w, x1, y2, z1, false);
									if (canSpawn == Event.Result.ALLOW || canSpawn == Event.Result.DEFAULT && entity.getCanSpawnHere() && entity.isNotColliding()) {
										if (!ForgeEventFactory.doSpecialSpawn(entity, w, x1, y2, z1)) {
											data = entity.onInitialSpawn(w.getDifficultyForLocation(pos.setPos(entity)), data);
										}

										if (entity.isNotColliding()) {
											++spawned1;
											w.spawnEntity(entity);
										} else {
											entity.setDead();
										}

										if (spawned1 >= ForgeEventFactory.getMaxSpawnPackSize(entity)) {
											continue label143;
										}
									}

									spawned += spawned1;
								}
							}
						}
					}
				}
			}
		}

		return spawned;
	}

	public static boolean func_185331_a(IBlockState state) {
		return !state.isBlockNormalCube() && !state.canProvidePower() && !state.getMaterial().isLiquid() && !BlockRailBase.isRailBlock(state);
	}

	public static boolean func_180267_a(EntityLiving.SpawnPlacementType type, World w, BlockPos pos) {
		return w.getWorldBorder().contains(pos) && type.canSpawnAt(w, pos);
	}

	public static boolean canCreatureTypeSpawnBody(EntityLiving.SpawnPlacementType type, World w, BlockPos pos) {
		IBlockState state1 = w.getBlockState(pos);
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
		if (type == EntityLiving.SpawnPlacementType.IN_WATER) {
			boolean flag = state1.getMaterial() == Material.WATER && w.getBlockState(pos1.move(EnumFacing.DOWN)).getMaterial() == Material.WATER && !w.getBlockState(pos1.move(EnumFacing.UP, 2))
																																					  .isNormalCube();
			pos1.release();
			return flag;
		} else {
			IBlockState state = w.getBlockState(pos1.move(EnumFacing.DOWN));
			if (!state.getBlock().canCreatureSpawn(state, w, pos1, type)) {
				return false;
			} else {
				Block block = w.getBlockState(pos1).getBlock();
				return block != Blocks.BEDROCK && block != Blocks.BARRIER && func_185331_a(state1) && func_185331_a(w.getBlockState(pos1.move(EnumFacing.UP, 2)));
			}
		}
	}

	public static void func_77191_a(World w, Biome biome, int cx, int cz, int dx, int dz, Random r) {
		List<Biome.SpawnListEntry> list = biome.getSpawnableList(EnumCreatureType.CREATURE);
		if (!list.isEmpty()) {
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
			while (r.nextFloat() < biome.getSpawningChance()) {
				Biome.SpawnListEntry entry = WeightedRandom.getRandomItem(r, list);
				int count = entry.minGroupCount + r.nextInt(1 + entry.maxGroupCount - entry.minGroupCount);
				IEntityLivingData data = null;
				int x = cx + r.nextInt(dx);
				int z = cz + r.nextInt(dz);
				int ox = x;
				int oz = z;

				while (count-- > 0) {
					for (int tries = 0; tries < 4; ++tries) {
						getTopSolidOrLiquidBlock(w, pos);
						if (func_180267_a(EntityLiving.SpawnPlacementType.ON_GROUND, w, pos)) {
							EntityLiving entity;
							try {
								entity = entry.newInstance(w);
							} catch (Exception e) {
								e.printStackTrace();
								continue;
							}

							if (ForgeEventFactory.canEntitySpawn(entity, w, x + 0.5F, pos.getY(), z + 0.5F, false) == Event.Result.DENY) {
								continue;
							}

							entity.setLocationAndAngles(x + 0.5F, pos.getY(), z + 0.5F, r.nextFloat() * 360, 0);
							w.spawnEntity(entity);
							data = entity.onInitialSpawn(w.getDifficultyForLocation(pos), data);
							break;
						}

						do {
							x = ox + r.nextInt(5) - r.nextInt(5);
							z = oz + r.nextInt(5) - r.nextInt(5);
						} while (x < cx || x >= cx + dx || z < cz || z >= cz + dx);
					}
				}
			}
			pos.release();
		}
	}

	public static void getTopSolidOrLiquidBlock(World w, BlockPos.PooledMutableBlockPos pos) {
		Chunk chunk = w.getChunk(pos);

		pos.setY(chunk.getTopFilledSegment() + 16);
		while (pos.getY() > 0) {
			IBlockState state = chunk.getBlockState(pos);
			if (state.getMaterial().blocksMovement() && !state.getBlock().isLeaves(state, w, pos) && !state.getBlock().isFoliage(w, pos)) {
				break;
			}
			pos = pos.move(EnumFacing.DOWN);
		}
		pos.move(EnumFacing.UP);
	}
}
