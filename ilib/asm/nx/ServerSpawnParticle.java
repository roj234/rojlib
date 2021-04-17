package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.ServerWorldEventHandler;
import net.minecraft.world.WorldServer;

/**
 * @author Roj233
 * @since 2022/5/24 23:36
 */
@Nixim("/")
class ServerSpawnParticle extends ServerWorldEventHandler {
	@Shadow
	private WorldServer world;

	ServerSpawnParticle() {
		super(null, null);
	}

	@Override
	@Inject("/")
	public void spawnParticle(int id, boolean ignoreRange, double x, double y, double z, double vx, double vy, double vz, int... par) {
		EnumParticleTypes type = EnumParticleTypes.getParticleFromId(id);

		if (type == null || type == EnumParticleTypes.SPELL_MOB || type == EnumParticleTypes.SPELL_MOB_AMBIENT) {
			return;
		}

		if (par.length == type.getArgumentCount()) {
			//numberOfParticles must be 0 so that the speed parameters are actually used and not
			//randomized in NetHandlerPlayClient#handleParticles.
			//The speed parameters are actually RGB values for anything potion related.
			world.spawnParticle(type, x, y, z, 0, vx, vy, vz, 1.0, par);
		}
	}
}
