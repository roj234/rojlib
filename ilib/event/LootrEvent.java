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
package ilib.event;

import ilib.block.BlockLootrChest;
import ilib.tile.TileEntityLootrChest;
import roj.collect.MyHashSet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @author Roj234
 * @since  2020/8/18 13:53
 */
public class LootrEvent {
    public static final MyHashSet<ResourceLocation> whiteList = new MyHashSet<>(
            new ResourceLocation("minecraft:chest"), new ResourceLocation("minecraft:hopper")
    );

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithChest(PlayerInteractEvent.RightClickBlock event) {
        final World world = event.getWorld();
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(event.getPos());
        if (tile instanceof TileEntityLockableLoot) {
            TileEntityLockableLoot loot = (TileEntityLockableLoot) tile;
            if (loot.getLootTable() != null) {
                ResourceLocation loc = TileEntity.getKey(tile.getClass());
                if (!whiteList.contains(loc)) return;
                // ???????????????
                world.removeTileEntity(event.getPos());
                world.setBlockState(event.getPos(), BlockLootrChest.INSTANCE);
                TileEntityLootrChest chest = (TileEntityLootrChest) world.getTileEntity(event.getPos());
                if (chest != null) {
                    chest.setLootTable(loot.getLootTable(), world.rand.nextLong());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDestroyLootrChest(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        IBlockState state = event.getState();
        if (state.getBlock() == BlockLootrChest.INSTANCE.getBlock()) {
            EntityPlayer player = event.getPlayer();
            if (!player.isSneaking()) {
                player.sendMessage(new TextComponentTranslation("tooltip.ilib.not_break"));
                event.setCanceled(true);
            } else {
                BlockPos pos = event.getPos();
                world.createExplosion(null, pos.getX(), pos.getY(), pos.getZ(), 3, true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntitySpawn(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) return;
        if (event.getEntity() instanceof EntityMinecartContainer) {
            EntityMinecartContainer container = (EntityMinecartContainer) event.getEntity();
            if (container.getLootTable() != null) {
                ResourceLocation loc = container.getLootTable();
                BlockPos pos = event.getEntity().getPosition();
                event.getWorld().setBlockState(pos, BlockLootrChest.INSTANCE);
                TileEntityLootrChest chest = (TileEntityLootrChest) event.getWorld().getTileEntity(pos);
                if (chest != null) {
                    chest.setLootTable(loc, event.getWorld().rand.nextLong());
                }
                event.setCanceled(true);
            }
        }
    }
}
