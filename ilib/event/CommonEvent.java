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

import com.google.common.collect.ImmutableList;
import ilib.Config;
import ilib.ImpLib;
import ilib.api.Ownable;
import ilib.api.mark.MUnbreakable;
import ilib.api.recipe.AnvilRecipe;
import ilib.capabilities.Capabilities;
import ilib.capabilities.EntitySize;
import ilib.item.ItemSelectTool;
import ilib.math.Arena;
import ilib.math.SelectionCache;
import ilib.tile.OwnerManager;
import ilib.util.EntityHelper;
import ilib.util.InventoryUtil;
import ilib.util.MCTexts;
import ilib.util.PlayerUtil;
import ilib.util.freeze.FreezedBlock;
import roj.collect.ToDoubleMap;
import roj.math.MathUtils;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CommonEvent {
    public static void init() {
        MinecraftForge.EVENT_BUS.register(CommonEvent.class);
        MinecraftForge.EVENT_BUS.register(DoorEvent.class);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityHurt(LivingHurtEvent event) {
        if(Config.siFrameTime < 0) return;

        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote) {
            return;
        }
        DamageSource source = event.getSource();
        Entity trueSource = source.getTrueSource();

        if (Config.siTimeExcludeTargets.contains(entity.getClass().toString())) {
            return;
        }

        // May have more DoTs missing in this list
        // Not anymore (/s)
        if (Config.siTimeExcludeDmgs.contains(source.getDamageType())) {
            return;
        }

        // Mobs that do damage on collusion but have no attack timer
        if (trueSource != null) {
            if (Config.siTimeExcludeAttackers.contains(trueSource.getClass().toString())) {
                return;
            }
        }

        entity.hurtResistantTime = Config.siFrameTime;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        float str = player.getCooledAttackStrength(0);
        if (str < Config.attackCD) event.setCanceled(true);
    }

    private static void updateTick(EntityLivingBase entity) {
        EntitySize cap = entity.getCapability(Capabilities.RENDERING_SIZE, null);
        if (cap != null) {
            double rh = cap.relHeight,
                    rw = cap.relWidth;
            float height = (float)(cap.defHeight * rh),
                    width = (float)(cap.defWidth * rw);
            if (rw != 1 || rh != 1) {
                cap.transformed = true;

                if(entity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entity;
                    float eyeHeight = (float) (player.getDefaultEyeHeight() * rh);
                    if (player.isSneaking()) {
                        height *= 0.9166667F;
                        eyeHeight *= 0.9382716F;
                    }
                    if (player.isElytraFlying())
                        height *= 0.33F;
                    if (player.isPlayerSleeping()) {
                        width = 0.2F;
                        height = 0.2F;
                    }
                    //if (player.isRiding()) {
                        //
                    //}
                    width = Math.max(width, 0.15F);
                    height = Math.max(height, 0.25F);
                    if (height >= 1.6F) {
                        player.eyeHeight = eyeHeight;
                    } else {
                        player.eyeHeight = eyeHeight * 0.9876542F;
                    }
                } else {
                    width = Math.max(width, 0.04F);
                    height = Math.max(height, 0.08F);
                }
                entity.height = height;
                entity.width = width;
                double d0 = width / 2.0D;
                AxisAlignedBB aabb = entity.getEntityBoundingBox();
                entity.setEntityBoundingBox(new AxisAlignedBB(entity.posX - d0, aabb.minY, entity.posZ - d0, entity.posX + d0, aabb.minY + entity.height, entity.posZ + d0));
            } else {
                if (cap.transformed) {
                    entity.height = height;
                    entity.width = width;
                    double d0 = width / 2.0D;
                    AxisAlignedBB aabb = entity.getEntityBoundingBox();
                    entity.setEntityBoundingBox(new AxisAlignedBB(entity.posX - d0, aabb.minY, entity.posZ - d0, entity.posX + d0, aabb.minY + height, entity.posZ + d0));

                    if(entity instanceof EntityPlayer) {
                        EntityPlayer player = (EntityPlayer) entity;
                        player.eyeHeight = player.getDefaultEyeHeight();
                    }
                    cap.transformed = false;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();

        if(Config.fixNaNHealth) {
            final float health = entity.getHealth();
            if(health != health || health == Float.POSITIVE_INFINITY || health == Float.NEGATIVE_INFINITY) {
                EntityHelper.remove(entity, null, EntityHelper.REMOVE_NOTIFY | EntityHelper.REMOVE_FORCE);
                return;
            }
        }

        if(entity.world.isRemote) return;

        updateTick(entity);
    }

    /*@SubscribeEvent 手动添加即可
    public static void onAddCapabilites(AttachCapabilitiesEvent<EntityLivingBase> event) {
        EntityLivingBase entity = event.getObject();
        if (!entity.hasCapability(Capabilities.RENDERING_SIZE, null)) {
            float defaultWidth = entity.width;
            float defaultHeight = entity.height;
            //EntityDisplaySize cap = new EntityDisplaySize(defaultWidth, defaultHeight);
            //event.addCapability(new ResourceLocation(ImpLib.MODID, "rendering_size"), cap);
        }
    }*/

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player.world.isRemote) return;
        if (event.toDim == -1 || event.toDim == 1) {
            AxisAlignedBB tpAABB = new AxisAlignedBB(event.player.posX - 16, event.player.posY - 16, event.player.posZ - 16,event.player.posX + 16, event.player.posY + 16, event.player.posZ + 16);
            List<EntityMinecartContainer> carts = event.player.world.getEntitiesWithinAABB(EntityMinecartContainer.class, tpAABB);
            for (EntityMinecartContainer cart : carts) {
                EntityHelper.remove(cart, cart.world); // no drop logic
                PlayerUtil.broadcastAll("[TEST-BUGFIX] Minecart Bug Fix... Kill a cart!");
            }
        }
    }

    static void clearRepairCost(ItemStack stack) {
        if (!stack.isEmpty() && stack.hasTagCompound())
            stack.getTagCompound().removeTag("RepairCost");
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (Config.noRepairCost)
            clearRepairCost(event.getItemResult());
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (Config.noRepairCost) {
            clearRepairCost(left);
            clearRepairCost(right);
        }

        if (Config.enchantOverload) {
            if (!left.isEmpty() && Items.ENCHANTED_BOOK == right.getItem()) {
                int cost = 0;
                Map<Enchantment, Integer> currentEnchants = EnchantmentHelper.getEnchantments(left);
                for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(right).entrySet()) {
                    Enchantment ench = entry.getKey();
                    int level = entry.getValue();
                    if (null == ench || 0 == level)
                        continue;
                    if (ilib.util.EnchantmentHelper.canStackEnchant(left, ench)) {
                        int currentLevel = 0;
                        if (currentEnchants.containsKey(ench))
                            currentLevel = currentEnchants.get(ench);
                        if (currentLevel > level)
                            continue;
                        int outLevel = (currentLevel == level) ? (level + 1) : level;
                        if (outLevel > currentLevel) {
                            currentEnchants.put(ench, outLevel);
                            cost += ilib.util.EnchantmentHelper.getApplyCost(ench, outLevel);
                        }
                    }
                }

                if (cost > 0) {
                    ItemStack out = left.copy();
                    EnchantmentHelper.setEnchantments(currentEnchants, out);
                    String name = event.getName();
                    if (name != null && !name.isEmpty()) {
                        out.setStackDisplayName(name);
                        cost++;
                    }
                    event.setOutput(out);
                    event.setCost(cost);
                } else {
                    event.setCanceled(true);
                }
                return;
            }
        }

        List<AnvilRecipe> anvilRecipes = AnvilRecipe.REGISTRY.get(left.getItem());
        if (anvilRecipes != null) {
            for (AnvilRecipe recipe : anvilRecipes) {
                if (InventoryUtil.areItemStacksEqual(recipe.getInput1(), left) && recipe.getInput1().getCount() == left.getCount()) {
                    if (InventoryUtil.areItemStacksEqual(recipe.getInput2(), right) && recipe.getInput2().getCount() >= right.getCount()) {
                        event.setCost(recipe.getExpCost());
                        event.setOutput(recipe.getOutput().copy());
                        event.setMaterialCost(recipe.getInput2().getCount());
                        PlayerUtil.broadcastAll("Found, output: " + recipe.getInput2());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.isCancelable()) return;
        if (event.getWorld().isRemote) {
            return;
        }

        BlockPos blockpos = event.getPos();
        EntityPlayer player = event.getEntityPlayer();
        TileEntity t = event.getWorld().getTileEntity(blockpos);
        if (t instanceof Ownable) {
            Ownable tile = (Ownable) t;
            if (tile.unOwned())
                return;

            long UUIDH = player.getUniqueID().getMostSignificantBits();
            long UUIDL = player.getUniqueID().getLeastSignificantBits();

            long UUIDCompareH = tile.getOwnerUUIDH();
            long UUIDCompareL = tile.getOwnerUUIDL();
            int ownerType = tile.getOwnType(); // 0 public 1 private 2 protected

            if (UUIDH != UUIDCompareH || UUIDL != UUIDCompareL) {
                if (player.capabilities.isCreativeMode) {
                    PlayerUtil.sendTo(player, "tooltip.ilib.bypass");
                    return;
                }
                if (ownerType == 1) {
                    PlayerUtil.sendTo(player, "tooltip.ilib.notpremission");
                    event.setCanceled(true);
                } else if (ownerType == 2) {
                    OwnerManager trustedList = tile.getTrustList();
                    for (int i = 0; i < trustedList.size(); i++) {
                        if (trustedList.getUUIDH(i) == UUIDH && trustedList.getUUIDL(i) == UUIDL) {
                            return;
                        }
                    }
                    PlayerUtil.sendTo(player, "tooltip.ilib.notpremission");
                    event.setCanceled(true);
                }
            }
            return;
        }

        IBlockState blockState = event.getWorld().getBlockState(blockpos);
        Block block = blockState.getBlock();

        if (block instanceof MUnbreakable) {
            if (player.getHeldItemMainhand().getItem() instanceof ItemBlock) {
                if (((ItemBlock) player.getHeldItemMainhand().getItem()).getBlock() == block)
                    return;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getItemStack().getItem() == ItemSelectTool.INSTANCE) {
            long UUID = event.getEntityPlayer().getUniqueID().getMostSignificantBits();
            Arena arena = SelectionCache.get(UUID);
            BlockPos pos = event.getPos();

            if (arena != null && pos.equals(arena.getP1())) return;
            int count = SelectionCache.set(UUID, 1, pos).getSelectionSize();

            if (event.getWorld().isRemote) {
                EntityPlayer player = event.getEntityPlayer();
                PlayerUtil.sendTo(player, MCTexts.format("command.ilib.sel.pos1") + ": " + pos.getX() + ',' + pos.getY() + ',' + pos.getZ());
                if (count > 0) PlayerUtil.sendTo(player, "command.ilib.sel.size", count);
                player.playSound(SoundEvents.BLOCK_NOTE_PLING, 1, 1);
            }

            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityDamage(LivingHurtEvent event) {
        Entity attacker0 = event.getSource().getTrueSource();
        if (attacker0 == null || attacker0.world.isRemote) return;

        if (Config.jumpAttack && attacker0 instanceof EntityPlayer) {
            float fallDistance = attacker0.fallDistance;
            if (fallDistance > 2.0F) {
                event.setAmount(event.getAmount() * (fallDistance / 2.0F));
                attacker0.fallDistance = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void fastLeaveDecayMod(BlockEvent.NeighborNotifyEvent event) {
        if (Config.fastLeaveDecay) {
            World world = event.getWorld();
            if (!world.isRemote) {
                for (EnumFacing facing : event.getNotifiedSides()) {
                    BlockPos blockPos = event.getPos().offset(facing);
                    if (!world.isBlockLoaded(blockPos)) continue; // Important
                    IBlockState blockState = world.getBlockState(blockPos);
                    Block block = blockState.getBlock();
                    if (block.isLeaves(blockState, world, blockPos)) {
                        world.scheduleUpdate(blockPos, block, 4 + world.rand.nextInt(7));
                    }
                }
            }
        }
    }

    protected static byte maxViewDistance;
    protected static PlayerList playerList;
    protected static float maxTickTime = 60;

    public static int checkTimer;

    public static void onServerStart(MinecraftServer server) {
        playerList = server.getPlayerList();
        maxViewDistance = (byte) playerList.getViewDistance();
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player.world.isRemote) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.openContainer != null) {
            player.openContainer.onContainerClosed(player);

            InventoryPlayer inventoryPlayer = player.inventory;
            // What is getItemStack() ?
            // Dragging Stack
            if (!inventoryPlayer.getItemStack().isEmpty()) {
                player.dropItem(inventoryPlayer.getItemStack(), false);
                inventoryPlayer.setItemStack(ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onMissingBlock(RegistryEvent.MissingMappings<Block> event) {
        ImmutableList<RegistryEvent.MissingMappings.Mapping<Block>> blocks = event.getAllMappings();
        for (int i = 0; i < blocks.size(); i++) {
            RegistryEvent.MissingMappings.Mapping<Block> map = blocks.get(i);
            if (map.getTarget() != Blocks.AIR)
            map.remap(new FreezedBlock().setRegistryName(map.key));
        }
    }

    private static ToDoubleMap<String> playerAcc = new ToDoubleMap<>();

    @SubscribeEvent
    public static void collisionDamage(TickEvent.ServerTickEvent event) {
        if(event.phase == TickEvent.Phase.END) return;

        double damageThreshold = 1e-4;
        double damageMultiplier = 10;
        boolean damageIsWall = true;

        List<EntityPlayerMP> players = PlayerUtil.getMinecraftServer().getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP player = players.get(i);
            if (player.isElytraFlying()) continue;
            if (player.isDead) {
                playerAcc.remove(player.getName());
                continue;
            }

            double mx = player.motionX;
            double mz = player.motionZ;
            double motion = mx * mx + mz * mz;

            double prevMotion = playerAcc.getOrDefault(player.getName(), 0);
            playerAcc.putDouble(player.getName(), motion);

            if (prevMotion != motion)
            System.out.println("delta motion " + prevMotion + " => " + motion);
            double delta = Math.abs(prevMotion - motion) - damageThreshold;
            if (delta > 0) {
                float damage = (float) (delta * damageMultiplier);
                if (damage != damage) return;

                player.playSound(
                    damage > 4 ? SoundEvents.ENTITY_GENERIC_BIG_FALL : SoundEvents.ENTITY_GENERIC_SMALL_FALL, 1, 1);
                player.attackEntityFrom(damageIsWall ? DamageSource.FLY_INTO_WALL : DamageSource.FALL, damage);
            }
        }
    }

    @SideOnly(Side.SERVER)
    @SubscribeEvent
    public static void dynamicViewDistance(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Config.dynamicViewDistance < 1) return;

        if (checkTimer < 20 * 30) {
            checkTimer++;
            return;
        }
        checkTimer = 0;

        if (playerList.getCurrentPlayerCount() == 0)
            return;

        double meanTickTime = MathUtils.average(playerList.getServerInstance().tickTimeArray) * 1.0E-6D;

        int currentViewDistance = playerList.getViewDistance();

        if (meanTickTime - 2.0D > maxTickTime && currentViewDistance > Config.dynamicViewDistance) {
            currentViewDistance--;
            playerList.setViewDistance(currentViewDistance);
        }
        if (meanTickTime + 2.0D < maxTickTime && currentViewDistance < maxViewDistance) {
            currentViewDistance++;
            playerList.setViewDistance(currentViewDistance);
        }
        ImpLib.logger().debug("Avg tick: " + (Math.round(meanTickTime * 100.0D) / 100L) + "ms  set view distance: " + currentViewDistance);
    }

    @SubscribeEvent
    public static void mobSpawnFull(LivingSpawnEvent.CheckSpawn event) {
        if (Config.mobSpawnFullBlock) {
            BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos
                .retain(event.getEntity().posX, event.getEntity().posY - 0.5, event.getEntity().posZ);
            IBlockState state = event.getWorld().getBlockState(pos);
            if (!state.isFullCube() && state.getCollisionBoundingBox(event.getWorld(), pos) != Block.NULL_AABB)
                event.setResult(Event.Result.DENY);
            pos.release();
        }
    }

}
