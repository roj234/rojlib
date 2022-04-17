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
package ilib;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.nbt.NBTBase;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.HashMap;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */

//!!AT [["net.minecraft.world.World", ["field_72988_C", "field_73020_y"]], ["net.minecraft.client.multiplayer.WorldClient", ["field_73033_b"]], ["net.minecraft.util.registry.RegistrySimple", ["field_82596_a"]], ["net.minecraft.client.renderer.BufferBuilder", ["field_179010_r"]]]
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

    // field_82596_a
    @SideOnly(Side.CLIENT)
    public static void clearRegistry(RegistrySimple<ModelResourceLocation, IBakedModel> model11) {
        IBakedModel v = model11.getObject(ModelBakery.MODEL_MISSING);
        model11.registryObjects = new HashMap<>();
        model11.registryObjects.put(ModelBakery.MODEL_MISSING, v);
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

    // field_179010_r
    @SideOnly(Side.CLIENT)
    public static boolean isDrawing(BufferBuilder b) {
        return b.isDrawing;
    }

    @SideOnly(Side.CLIENT)
    public static void setDrawing(BufferBuilder b, boolean isDrawing) {
        b.isDrawing = isDrawing;
    }

    public static void registerNetworkPacket(EnumConnectionState state, Class<? extends Packet<?>> packetClass) {
        state.registerPacket(EnumPacketDirection.CLIENTBOUND, packetClass);
        state.registerPacket(EnumPacketDirection.SERVERBOUND, packetClass);
        EnumConnectionState.STATES_BY_CLASS.put(packetClass, state);
    }

    public static void registerNetworkPacket(EnumConnectionState state, Class<? extends Packet<?>> packetClass, boolean toClient) {
        state.registerPacket(toClient ? EnumPacketDirection.CLIENTBOUND : EnumPacketDirection.SERVERBOUND, packetClass);
        EnumConnectionState.STATES_BY_CLASS.put(packetClass, state);
    }
}
