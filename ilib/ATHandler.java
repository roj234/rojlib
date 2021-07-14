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
import net.minecraft.client.resources.SimpleReloadableResourceManager;
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
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
/*@OpenAny(value = "net.minecraft.client.resources:SimpleReloadableResourceManager", names = {
        "clearResources", "func_110543_a",
        "notifyReloadListeners", "func_110544_b"
})
@OpenAny(value = "net.minecraft.world:World", names = {
        "chunkProvider", "mapStorage",
        "field_72988_C", "field_73020_y"
})
@OpenAny(value = "net.minecraft.client.multiplayer:WorldClient", names = {
        "clientChunkProvider", "field_73033_b"
})
@OpenAny("net.minecraft.client.renderer:BufferBuilder")*/
public class ATHandler {
    public static void setChunkProvider(World world, IChunkProvider provider) {
        world.chunkProvider = provider;
    }

    @SuppressWarnings("unchecked")
    public static void addCapabilities(CapabilityDispatcher dispatcher, ICapabilityProvider provIn, @Nullable String idIn) {
        ICapabilityProvider[] providers = new ICapabilityProvider[dispatcher.caps.length + 1];
        System.arraycopy(dispatcher.caps, 0, providers, 0, providers.length);
        providers[providers.length - 1] = provIn;
        dispatcher.caps = providers;

        if (idIn != null) {
            INBTSerializable<NBTBase>[] writers = (INBTSerializable<NBTBase>[]) new INBTSerializable<?>[dispatcher.writers.length];
            System.arraycopy(dispatcher.writers, 0, writers, 0, writers.length);
            writers[writers.length - 1] = (INBTSerializable<NBTBase>) provIn;
            dispatcher.writers = writers;

            String[] names = new String[dispatcher.names.length];
            System.arraycopy(dispatcher.names, 0, names, 0, names.length);
            names[names.length - 1] = idIn;
            dispatcher.names = names;
        }
    }

    @SideOnly(Side.CLIENT)
    public static void setClientChunkProvider(WorldClient world, ChunkProviderClient provider) {
        world.clientChunkProvider = provider;
    }

    public static void setMapStorage(World world, MapStorage storage) {
        world.mapStorage = storage;
    }

    @SideOnly(Side.CLIENT)
    public static void notifyReloadListeners(SimpleReloadableResourceManager manager) {
        manager.notifyReloadListeners();
    }

    @SideOnly(Side.CLIENT)
    public static void clearResources(SimpleReloadableResourceManager manager) {
        manager.clearResources();
    }

    @SideOnly(Side.CLIENT)
    public static void growBuffer(BufferBuilder b, int size) {
        b.growBuffer(size);
    }

    @SideOnly(Side.CLIENT)
    public static void setVertexCount(BufferBuilder b, int count) {
        b.vertexCount = count;
    }

    @SideOnly(Side.CLIENT)
    public static boolean isDrawing(BufferBuilder b) {
        return b.isDrawing;
    }

    @SideOnly(Side.CLIENT)
    public static void setDrawing(BufferBuilder b, boolean isDrawing) {
        b.isDrawing = isDrawing;
    }

    @SideOnly(Side.CLIENT)
    public static void setRawBuffer(BufferBuilder b, byte[] bytes) {
        b.byteBuffer.clear();
        b.growBuffer(bytes.length);
        b.vertexCount = bytes.length / b.vertexFormat.getSize();
        b.byteBuffer.put(bytes);
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
