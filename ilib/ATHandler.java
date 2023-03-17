package ilib;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTBase;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */

//!!AT [["net.minecraft.world.World", ["field_72988_C", "field_73020_y"]], ["net.minecraft.client.multiplayer.WorldClient", ["field_73033_b"]]]]
public class ATHandler {
	// field_72988_C
	public static void setChunkProvider(World world, IChunkProvider provider) {
		world.chunkProvider = provider;
	}

	// field_73020_y
	public static void setMapStorage(World world, MapStorage storage) {
		world.mapStorage = storage;
	}

	public static MapStorage getMapStorage(World world) {
		return world.mapStorage;
	}

	// field_73033_b
	@SideOnly(Side.CLIENT)
	public static void setClientChunkProvider(WorldClient world, ChunkProviderClient provider) {
		world.clientChunkProvider = provider;
	}

	@SuppressWarnings("unchecked")
	public static void addCapabilities(CapabilityDispatcher dp, ICapabilityProvider provIn, @Nullable String idIn) {
		ICapabilityProvider[] providers = new ICapabilityProvider[dp.caps.length + 1];
		System.arraycopy(dp.caps, 0, providers, 0, dp.caps.length);
		providers[dp.caps.length] = provIn;
		dp.caps = providers;

		if (idIn != null) {
			INBTSerializable<NBTBase>[] writers = (INBTSerializable<NBTBase>[]) new INBTSerializable<?>[dp.writers.length + 1];
			System.arraycopy(dp.writers, 0, writers, 0, dp.writers.length);
			writers[dp.writers.length] = (INBTSerializable<NBTBase>) provIn;
			dp.writers = writers;

			String[] names = new String[dp.names.length + 1];
			System.arraycopy(dp.names, 0, names, 0, dp.names.length);
			names[dp.names.length] = idIn;
			dp.names = names;
		}
	}

	public static void registerNetworkPacket(EnumConnectionState state, Class<? extends Packet<?>> type) {
		registerNetworkPacket(state, type, null);
	}

	public static void registerNetworkPacket(EnumConnectionState state, Class<? extends Packet<?>> type, Boolean toClient) {
		if (toClient != Boolean.FALSE) state.registerPacket(EnumPacketDirection.CLIENTBOUND, type);
		if (toClient != Boolean.TRUE) state.registerPacket(EnumPacketDirection.SERVERBOUND, type);
		EnumConnectionState.STATES_BY_CLASS.put(type, state);
	}
}
