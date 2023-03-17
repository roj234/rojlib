package ilib.asm.nx;

import ilib.Config;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;

import java.util.Random;

@Nixim("/")
abstract class NoUnusedChunks extends Chunk {
	public NoUnusedChunks() {
		super(null, 0, 0);
	}

	@Override
	@Inject(at = Inject.At.TAIL)
	protected void populate(IChunkGenerator generator) {
		if (getWorld().isRemote) {
			System.out.println("111111");
			return;
		}

		if (Config.chunkSaveChance > 0) {
			Random rnd = new Random();
			if (Config.chunkSaveChance < rnd.nextInt(100)) return;
		}

		getWorld().getMinecraftServer().addScheduledTask(() -> {
			setModified(false);
		});
	}

	@Override
	@Inject(at = Inject.At.TAIL)
	public void generateSkylightMap() {
		if (getWorld().isRemote) {
			System.out.println("111111");
			return;
		}

		if (Config.chunkSaveChance > 0) {
			Random rnd = new Random();
			if (Config.chunkSaveChance < rnd.nextInt(100)) return;
		}

		getWorld().getMinecraftServer().addScheduledTask(() -> {
			setModified(false);
		});
	}
}
