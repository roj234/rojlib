package ilib.world.deco;

import net.minecraft.block.BlockFlower;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.gen.ChunkGeneratorSettings;
import net.minecraft.world.gen.feature.*;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.TerrainGen;

import java.util.Random;

import static net.minecraftforge.event.terraingen.DecorateBiomeEvent.Decorate.EventType.*;
import static net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable.EventType.*;

/**
 * @author Roj234
 */
public class FastBiomeDecorator extends BiomeDecorator {
	static WorldGenLiquids water = new WorldGenLiquids(Blocks.FLOWING_WATER);
	static WorldGenLiquids lava = new WorldGenLiquids(Blocks.FLOWING_LAVA);
	static WorldGenPumpkin pumpkin = new WorldGenPumpkin();
	static WorldGenDeadBush dead_bush = new WorldGenDeadBush();

	public FastBiomeDecorator() {}

	public FastBiomeDecorator(BiomeDecorator decorator) {
		copy(decorator, this);
	}

	private int prevWorld = Integer.MAX_VALUE;

	public static void copy(BiomeDecorator old, BiomeDecorator now) {
		now.clayGen = old.clayGen;
		now.sandGen = old.sandGen;
		now.gravelGen = old.gravelGen;
		now.dirtGen = old.dirtGen;
		now.gravelOreGen = old.gravelOreGen;
		now.graniteGen = old.graniteGen;
		now.dioriteGen = old.dioriteGen;
		now.andesiteGen = old.andesiteGen;
		now.coalGen = old.coalGen;
		now.ironGen = old.ironGen;
		now.goldGen = old.goldGen;
		now.redstoneGen = old.redstoneGen;
		now.diamondGen = old.diamondGen;
		now.lapisGen = old.lapisGen;
		now.flowerGen = old.flowerGen;
		now.mushroomBrownGen = old.mushroomBrownGen;
		now.mushroomRedGen = old.mushroomRedGen;
		now.bigMushroomGen = old.bigMushroomGen;
		now.reedGen = old.reedGen;
		now.cactusGen = old.cactusGen;
		now.waterlilyGen = old.waterlilyGen;

		now.waterlilyPerChunk = old.waterlilyPerChunk;
		now.treesPerChunk = old.treesPerChunk;
		now.extraTreeChance = old.extraTreeChance;
		now.flowersPerChunk = old.flowersPerChunk;
		now.grassPerChunk = old.grassPerChunk;
		now.deadBushPerChunk = old.deadBushPerChunk;
		now.mushroomsPerChunk = old.mushroomsPerChunk;
		now.reedsPerChunk = old.reedsPerChunk;
		now.cactiPerChunk = old.cactiPerChunk;
		now.gravelPatchesPerChunk = old.gravelPatchesPerChunk;
		now.sandPatchesPerChunk = old.sandPatchesPerChunk;
		now.clayPerChunk = old.clayPerChunk;
		now.bigMushroomsPerChunk = old.bigMushroomsPerChunk;
		now.generateFalls = old.generateFalls;
	}

	@Override
	public void decorate(World world, Random rand, Biome biome, BlockPos pos) {
		if (decorating) {
			throw new RuntimeException("Already decorating");
		}
		if (this.prevWorld != world.provider.getDimension()) {
			super.decorate(world, rand, biome, pos);
			this.prevWorld = world.provider.getDimension();
		}
		this.chunkPos = pos;
		genDecorations(biome, world, rand);
		decorating = false;
	}

	protected void genDecorations(Biome b, World w, Random rnd) {
		BlockPos pos = this.chunkPos;
		ChunkPos cp = new ChunkPos(pos);

		MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Pre(w, rnd, cp));
		this.generateOres(w, rnd);

		int cnt;
		BlockPos.PooledMutableBlockPos pmb = BlockPos.PooledMutableBlockPos.retain();
		if (TerrainGen.decorate(w, rnd, cp, SAND)) {
			cnt = sandPatchesPerChunk;
			WorldGenerator gen = sandGen;
			while (cnt-- > 0) {
				gen.generate(w, rnd, w.getTopSolidOrLiquidBlock(plus(pos, pmb, rnd)));
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, CLAY)) {
			cnt = clayPerChunk;
			WorldGenerator gen = clayGen;
			while (cnt-- > 0) {
				gen.generate(w, rnd, w.getTopSolidOrLiquidBlock(plus(pos, pmb, rnd)));
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, SAND_PASS2)) {
			cnt = gravelPatchesPerChunk;
			WorldGenerator gen = gravelGen;
			while (cnt-- > 0) {
				gen.generate(w, rnd, w.getTopSolidOrLiquidBlock(plus(pos, pmb, rnd)));
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, TREE)) {
			cnt = treesPerChunk;
			if (rnd.nextFloat() < extraTreeChance) {
				++cnt;
			}
			while (cnt-- > 0) {
				plus(pos, pmb, rnd);
				WorldGenAbstractTree gen = b.getRandomTreeFeature(rnd);
				gen.setDecorationDefaults();
				BlockPos pos1 = w.getHeight(pmb);
				if (gen.generate(w, rnd, pos1)) {
					gen.generateSaplings(w, rnd, pos1);
				}
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, BIG_SHROOM)) {
			cnt = bigMushroomsPerChunk;
			WorldGenerator gen = bigMushroomGen;
			while (cnt-- > 0) {
				gen.generate(w, rnd, w.getTopSolidOrLiquidBlock(plus(pos, pmb, rnd)));
			}
		}

		int x1;
		int z;
		int y;
		if (TerrainGen.decorate(w, rnd, cp, FLOWERS)) {
			cnt = flowersPerChunk;
			while (cnt-- > 0) {
				x1 = rnd.nextInt(16) + 8;
				z = rnd.nextInt(16) + 8;
				y = w.getHeight(plus(pos, pmb, x1, 0, z)).getY() + 32;
				if (y > 0) {
					plus(pos, pmb, x1, rnd.nextInt(y), z);
					BlockFlower.EnumFlowerType type = b.pickRandomFlower(rnd, pmb);
					BlockFlower block = type.getBlockType().getBlock();
					if (block.getDefaultState().getMaterial() != Material.AIR) {
						flowerGen.setGeneratedBlock(block, type);
						flowerGen.generate(w, rnd, pmb);
					}
				}
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, GRASS)) {
			cnt = grassPerChunk;
			while (cnt-- > 0) {
				x1 = rnd.nextInt(16) + 8;
				z = rnd.nextInt(16) + 8;
				y = w.getHeight(plus(pos, pmb, x1, 0, z)).getY() * 2;
				if (y > 0) {
					b.getRandomWorldGenForGrass(rnd).generate(w, rnd, plus(pos, pmb, x1, rnd.nextInt(y), z));
				}
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, DEAD_BUSH)) {
			cnt = deadBushPerChunk;
			while (cnt-- > 0) {
				x1 = rnd.nextInt(16) + 8;
				z = rnd.nextInt(16) + 8;
				y = w.getHeight(plus(pos, pmb, x1, 0, z)).getY() * 2;
				if (y > 0) {
					dead_bush.generate(w, rnd, plus(pos, pmb, x1, rnd.nextInt(y), z));
				}
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, LILYPAD)) {
			cnt = waterlilyPerChunk;
			WorldGenerator gen = waterlilyGen;
			while (cnt-- > 0) {
				x1 = rnd.nextInt(16) + 8;
				z = rnd.nextInt(16) + 8;
				y = w.getHeight(plus(pos, pmb, x1, 0, z)).getY() * 2;
				if (y > 0) {
					plus(pos, pmb, x1, rnd.nextInt(y), z);
					while (pmb.getY() > 0) {
						pmb.move(EnumFacing.DOWN);
						if (!w.isAirBlock(pmb)) break;
					}

					gen.generate(w, rnd, pmb);
				}
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, SHROOM)) {
			cnt = mushroomsPerChunk + 1;
			WorldGenerator gen = mushroomBrownGen;
			WorldGenerator gen2 = mushroomRedGen;

			while (cnt-- > 0) {
				if (rnd.nextInt(4) == 0) {
					gen.generate(w, rnd, w.getHeight(plus(pos, pmb, rnd)));
				}

				if (rnd.nextInt(8) == 0) {
					x1 = rnd.nextInt(16) + 8;
					z = rnd.nextInt(16) + 8;
					y = w.getHeight(plus(pos, pmb, x1, 0, z)).getY() * 2;
					if (y > 0) {
						gen2.generate(w, rnd, plus(pos, pmb, x1, rnd.nextInt(y), z));
					}
				}
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, REED)) {
			cnt = reedsPerChunk + 10;
			WorldGenerator gen = reedGen;
			while (cnt-- > 0) {
				x1 = rnd.nextInt(16) + 8;
				z = rnd.nextInt(16) + 8;
				y = w.getHeight(plus(pos, pmb, x1, 0, z)).getY() * 2;
				if (y > 0) {
					gen.generate(w, rnd, plus(pos, pmb, x1, rnd.nextInt(y), z));
				}
			}
		}

		if (rnd.nextInt(32) == 0 && TerrainGen.decorate(w, rnd, cp, PUMPKIN)) {
			int x = rnd.nextInt(16) + 8;
			x1 = rnd.nextInt(16) + 8;
			z = w.getHeight(plus(pos, pmb, x, 0, x1)).getY() * 2;
			if (z > 0) {
				pumpkin.generate(w, rnd, plus(pos, pmb, x, rnd.nextInt(z), x1));
			}
		}

		if (TerrainGen.decorate(w, rnd, cp, CACTUS)) {
			cnt = cactiPerChunk;
			WorldGenerator gen = this.cactusGen;
			while (cnt-- > 0) {
				x1 = rnd.nextInt(16) + 8;
				z = rnd.nextInt(16) + 8;
				y = w.getHeight(plus(pos, pmb, x1, 0, z)).getY() * 2;
				if (y > 0) {
					gen.generate(w, rnd, plus(pos, pmb, x1, rnd.nextInt(y), z));
				}
			}
		}

		if (generateFalls) {
			if (TerrainGen.decorate(w, rnd, cp, LAKE_WATER)) {
				WorldGenLiquids water = FastBiomeDecorator.water;
				for (int x = 0; x < 50; ++x) {
					x1 = rnd.nextInt(16) + 8;
					z = rnd.nextInt(16) + 8;
					y = rnd.nextInt(248) + 8;
					if (y > 0) {
						water.generate(w, rnd, plus(pos, pmb, x1, rnd.nextInt(y), z));
					}
				}
			}

			if (TerrainGen.decorate(w, rnd, cp, LAKE_LAVA)) {
				WorldGenLiquids lava = FastBiomeDecorator.lava;
				for (int x = 0; x < 20; ++x) {
					x1 = rnd.nextInt(16) + 8;
					z = rnd.nextInt(16) + 8;
					y = rnd.nextInt(rnd.nextInt(rnd.nextInt(240) + 8) + 8);
					lava.generate(w, rnd, plus(pos, pmb, x1, y, z));
				}
			}
		}
		pmb.release();

		MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Post(w, rnd, cp));
	}

	private static BlockPos plus(BlockPos f, BlockPos.PooledMutableBlockPos pmb, int x, int y, int z) {
		return pmb.setPos(f.getX() + x, f.getY() + y, f.getZ() + z);
	}

	private static BlockPos plus(BlockPos f, BlockPos.PooledMutableBlockPos pmb, Random r) {
		return pmb.setPos(f.getX() + r.nextInt(16) + 8, f.getY(), f.getZ() + r.nextInt(16) + 8);
	}

	@Override
	protected void generateOres(World w, Random rnd) {
		MinecraftForge.ORE_GEN_BUS.post(new OreGenEvent.Pre(w, rnd, chunkPos));

		ChunkGeneratorSettings cps = chunkProviderSettings;
		if (TerrainGen.generateOre(w, rnd, dirtGen, chunkPos, DIRT)) {
			genStandardOre1(w, rnd, cps.dirtCount, dirtGen, cps.dirtMinHeight, cps.dirtMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, gravelOreGen, chunkPos, GRAVEL)) {
			genStandardOre1(w, rnd, cps.gravelCount, gravelOreGen, cps.gravelMinHeight, cps.gravelMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, dioriteGen, chunkPos, DIORITE)) {
			genStandardOre1(w, rnd, cps.dioriteCount, dioriteGen, cps.dioriteMinHeight, cps.dioriteMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, graniteGen, chunkPos, GRANITE)) {
			genStandardOre1(w, rnd, cps.graniteCount, graniteGen, cps.graniteMinHeight, cps.graniteMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, andesiteGen, chunkPos, ANDESITE)) {
			genStandardOre1(w, rnd, cps.andesiteCount, andesiteGen, cps.andesiteMinHeight, cps.andesiteMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, coalGen, chunkPos, COAL)) {
			genStandardOre1(w, rnd, cps.coalCount, coalGen, cps.coalMinHeight, cps.coalMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, ironGen, chunkPos, IRON)) {
			genStandardOre1(w, rnd, cps.ironCount, ironGen, cps.ironMinHeight, cps.ironMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, goldGen, chunkPos, GOLD)) {
			genStandardOre1(w, rnd, cps.goldCount, goldGen, cps.goldMinHeight, cps.goldMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, redstoneGen, chunkPos, REDSTONE)) {
			genStandardOre1(w, rnd, cps.redstoneCount, redstoneGen, cps.redstoneMinHeight, cps.redstoneMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, diamondGen, chunkPos, DIAMOND)) {
			genStandardOre1(w, rnd, cps.diamondCount, diamondGen, cps.diamondMinHeight, cps.diamondMaxHeight);
		}

		if (TerrainGen.generateOre(w, rnd, lapisGen, chunkPos, LAPIS)) {
			genStandardOre2(w, rnd, cps.lapisCount, lapisGen, cps.lapisCenterHeight, cps.lapisSpread);
		}

		MinecraftForge.ORE_GEN_BUS.post(new OreGenEvent.Post(w, rnd, chunkPos));
	}

	protected void genStandardOre1(World w, Random rnd, int cnt, WorldGenerator gen, int min, int max) {
		int j;
		if (max < min) {
			j = min;
			min = max;
			max = j;
		} else if (max == min) {
			if (min < 255) {
				++max;
			} else {
				--min;
			}
		}

		max -= min;

		BlockPos.PooledMutableBlockPos pmb = BlockPos.PooledMutableBlockPos.retain();
		BlockPos pos = chunkPos;
		while (cnt-- > 0) {
			gen.generate(w, rnd, pmb.setPos(pos.getX() + rnd.nextInt(16), pos.getY() + rnd.nextInt(max) + min, pos.getZ() + rnd.nextInt(16)));
		}
		pmb.release();
	}

	protected void genStandardOre2(World w, Random rnd, int cnt, WorldGenerator gen, int center, int dy) {
		center -= dy;

		BlockPos.PooledMutableBlockPos pmb = BlockPos.PooledMutableBlockPos.retain();
		BlockPos pos = chunkPos;
		while (cnt-- > 0) {
			gen.generate(w, rnd, pmb.setPos(pos.getX() + rnd.nextInt(16), pos.getY() + rnd.nextInt(dy) + rnd.nextInt(dy) + center, pos.getZ() + rnd.nextInt(16)));
		}
		pmb.release();
	}
}