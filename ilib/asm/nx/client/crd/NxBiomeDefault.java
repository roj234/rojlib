package ilib.asm.nx.client.crd;

import ilib.asm.util.EventRecycleBin;
import ilib.asm.util.MCHooksClient;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.world.biome.Biome;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.BiomeEvent;

/**
 * @author Roj233
 * @since 2022/4/22 22:06
 */
@Nixim("/")
class NxBiomeDefault extends Biome {
	@Shadow
	private int waterColor;

	public NxBiomeDefault() {
		super(null);
	}

	@Inject("/")
	public int getWaterColorMultiplier() {
		EventRecycleBin inst = EventRecycleBin.getLocalInstance();
		BiomeEvent.GetWaterColor event = inst.take(BiomeEvent.GetWaterColor.class);
		if (event == null) {event = new BiomeEvent.GetWaterColor(this, waterColor);} else MCHooksClient.get().setEvent(event, this, waterColor);
		MinecraftForge.EVENT_BUS.post(event);
		int color = event.getNewColor();
		inst.recycle(event);
		return color;
	}

	@Inject("/")
	public int getModdedBiomeGrassColor(int original) {
		EventRecycleBin inst = EventRecycleBin.getLocalInstance();
		BiomeEvent.GetGrassColor event = inst.take(BiomeEvent.GetGrassColor.class);
		if (event == null) {event = new BiomeEvent.GetGrassColor(this, original);} else MCHooksClient.get().setEvent(event, this, original);
		MinecraftForge.EVENT_BUS.post(event);
		int color = event.getNewColor();
		inst.recycle(event);
		return color;
	}

	@Inject("/")
	public int getModdedBiomeFoliageColor(int original) {
		EventRecycleBin inst = EventRecycleBin.getLocalInstance();
		BiomeEvent.GetFoliageColor event = inst.take(BiomeEvent.GetFoliageColor.class);
		if (event == null) {event = new BiomeEvent.GetFoliageColor(this, original);} else MCHooksClient.get().setEvent(event, this, original);
		MinecraftForge.EVENT_BUS.post(event);
		int color = event.getNewColor();
		inst.recycle(event);
		return color;
	}
}
