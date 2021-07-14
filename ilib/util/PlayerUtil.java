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

package ilib.util;

import ilib.ImpLib;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.server.management.UserListOps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/22 19:45
 */
public class PlayerUtil {
    public static List<EntityPlayerMP> getOnlinePlayers() {
        return getMinecraftServer().getPlayerList().getPlayers();
    }

    public static boolean isOpped(EntityPlayer player) {
        return getOPList().getEntry(player.getGameProfile()) != null;
    }

    public static UserListOps getOPList() {
        return getMinecraftServer().getPlayerList().getOppedPlayers();
    }

    public static void giveToPlayer(EntityPlayer player, ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, true);
        }
    }

    public static boolean isPlayerOnline(UUID uuid) {
        return getMinecraftServer().getPlayerList().getPlayerByUUID(uuid) != null;
    }

    @Nullable
    public static EntityPlayerMP getPlayer(UUID uuid) {
        return getMinecraftServer().getPlayerList().getPlayerByUUID(uuid);
    }

    public static List<EntityPlayerMP> getAllPlayersWatchingBlock(World world, BlockPos pos) {
        return getAllPlayersWatchingChunk(world, pos.getX(), pos.getZ());
    }

    public static EntityPlayerMP playerToMP(EntityPlayer player) {
        MinecraftServer server = getServerForPlayer(player);

        return server.getPlayerList().getPlayerByUsername(player.getGameProfile().getName());
    }

    public static void broadcastAll(ITextComponent component) {
        Integer[] it = DimensionManager.getIDs();

        for (Integer integer : it) {
            World world = DimensionManager.getWorld(integer);
            if (world == null) continue;
            for (EntityPlayer player : world.playerEntities) {
                player.sendStatusMessage(component, false);
            }
        }
    }

    public static void broadcastAll(String s) {
        broadcastAll(new TextComponentString(s));
    }

    public static void broadcastInRadius(World world, int x, int y, int z, ITextComponent component, float radius) {
        for (EntityPlayer player : world.playerEntities) {
            double sqdist = player.getDistanceSq((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D);
            if (sqdist < (double) radius) {
                player.sendStatusMessage(component, false);
            }
        }
    }

    public static void broadcastDimension(World world, ITextComponent component) {
        for (EntityPlayer player : world.playerEntities) {
            if (player.world.provider.getDimension() == world.provider.getDimension()) {
                player.sendStatusMessage(component, false);
            }
        }
    }

    public static EntityPlayerMP stringToPlayer(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByUsername(name);
    }

    public static MinecraftServer getServerForPlayer(EntityPlayer player) {
        return player.getEntityWorld().getMinecraftServer();
    }

    public static List<EntityPlayerMP> getAllPlayersWatchingChunk(World world, int x, int z) {
        if (world instanceof WorldServer) {
            PlayerChunkMap playerManager = ((WorldServer) world).getPlayerChunkMap();
            PlayerChunkMapEntry entry = playerManager.getEntry(x >> 4, z >> 4);
            return entry == null ? Collections.emptyList() : entry.getWatchingPlayers();
        } else {
            return null;
        }
    }

    public static List<EntityPlayerMP> getAllPlayersInDimension(int dimension) {
        World world = DimensionHelper.getWorldForDimension(null, dimension);
        if (world != null) {
            return Helpers.cast(new ArrayList<>(world.playerEntities));
        } else {
            List<EntityPlayerMP> list = new ArrayList<>();
            for (EntityPlayerMP player : getOnlinePlayers()) {
                if (player.world.provider.getDimension() == dimension)
                    list.add(player);
            }
            return list;
        }
    }

    public static List<EntityPlayerMP> getAllPlayersInDimension(World world) {
        return Helpers.cast(new ArrayList<>(world.playerEntities));
    }

    public static void sendTo(ICommandSender player, String value) {
        if (player == null) {
            if (!ImpLib.isClient)
                return;
            player = Minecraft.getMinecraft().player;
            if (player == null) {
                ImpLib.logger().catching(new RuntimeException("Tried to send message while client player is null"));
                return;
            }
        }
        player.sendMessage(new TextComponentTranslation(value));
    }

    public static void sendTo(ICommandSender player, String value, Object... brackets) {
        if (player == null) {
            if (!ImpLib.isClient)
                return;
            player = Minecraft.getMinecraft().player;
        }
        player.sendMessage(new TextComponentTranslation(value, brackets));
    }

    public static MinecraftServer getMinecraftServer() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }
}
