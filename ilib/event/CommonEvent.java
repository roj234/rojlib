package ilib.event;

import ilib.Config;
import ilib.ImpLib;
import ilib.api.Ownable;
import ilib.api.mark.MUnbreakable;
import ilib.api.recipe.AnvilRecipe;
import ilib.util.EntityHelper;
import ilib.util.InventoryUtil;
import ilib.util.PlayerUtil;
import ilib.world.deco.FastBiomeDecorator;
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
import net.minecraft.init.Items;
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
import net.minecraft.world.biome.BiomeDecorator;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.terraingen.BiomeEvent;
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

import static ilib.Config.worldGenOpt;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CommonEvent {
	public static void init() {
		if (worldGenOpt) MinecraftForge.TERRAIN_GEN_BUS.register(CommonEvent.class);
		MinecraftForge.EVENT_BUS.register(CommonEvent.class);
		if (Config.doorMod) MinecraftForge.EVENT_BUS.register(DoorEvent.class);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onCreateDeco(BiomeEvent.CreateDecorator event) {
		if (event.getNewBiomeDecorator().getClass() == BiomeDecorator.class) {
			event.setNewBiomeDecorator(new FastBiomeDecorator(event.getOriginalBiomeDecorator()));
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onEntityHurt(LivingHurtEvent event) {
		if (Config.siFrameTime < 0) return;

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

	@SubscribeEvent
	public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
		EntityLivingBase entity = event.getEntityLiving();

		if (Config.fixNaNHealth) {
			if (killIfNaN(entity, entity.getHealth())) return;
			if (killIfNaN(entity, entity.getAbsorptionAmount())) return;
			if (entity.posX != entity.posX) entity.posX = 0;
			if (entity.posY != entity.posY) entity.posY = 0;
			if (entity.posZ != entity.posZ) entity.posZ = 0;
		}
	}

	private static boolean killIfNaN(EntityLivingBase entity, float c) {
		if (c != c || c == Float.POSITIVE_INFINITY || c == Float.NEGATIVE_INFINITY) {
			entity.setHealth(0);
			entity.setAbsorptionAmount(0);
			EntityHelper.remove(entity, null, EntityHelper.REMOVE_NOTIFY | EntityHelper.REMOVE_FORCE);
			return true;
		}
		return false;
	}

	@SubscribeEvent
	public static void onLivingAttack(LivingHurtEvent event) {
		if (event.getAmount() != event.getAmount()) {
			DamageSource s = event.getSource();
			ImpLib.logger().fatal("实体{}受到的伤害是NaN, 类型 {} 来源 {}", event.getEntity(), s.getDamageType(), s.getTrueSource(), new Error());
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.player.world.isRemote) return;
		if (event.toDim == -1 || event.toDim == 1) {
			AxisAlignedBB tpAABB = new AxisAlignedBB(event.player.posX - 16, event.player.posY - 16, event.player.posZ - 16, event.player.posX + 16, event.player.posY + 16, event.player.posZ + 16);
			List<EntityMinecartContainer> carts = event.player.world.getEntitiesWithinAABB(EntityMinecartContainer.class, tpAABB);
			for (EntityMinecartContainer cart : carts) {
				EntityHelper.remove(cart, cart.world); // no drop logic
				PlayerUtil.broadcastAll("[TEST-BUGFIX] Minecart Bug Fix... Kill a cart!");
			}
		}
	}

	static void clearRepairCost(ItemStack stack) {
		if (!stack.isEmpty() && stack.hasTagCompound()) stack.getTagCompound().removeTag("RepairCost");
	}

	@SubscribeEvent
	public static void onAnvilRepair(AnvilRepairEvent event) {
		if (Config.noRepairCost) clearRepairCost(event.getItemResult());
	}

	@SubscribeEvent
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
					if (null == ench || 0 == level) continue;
					if (ilib.util.EnchantmentHelper.canStackEnchant(left, ench)) {
						int currentLevel = 0;
						if (currentEnchants.containsKey(ench)) currentLevel = currentEnchants.get(ench);
						if (currentLevel > level) continue;
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
		if (event.getWorld().isRemote) return;

		BlockPos pos = event.getPos();
		EntityPlayer player = event.getEntityPlayer();
		TileEntity t = event.getWorld().getTileEntity(pos);
		if (t instanceof Ownable) {
			Ownable tile = (Ownable) t;
			if (tile.getOwnerManager() == null) return;

			if (!tile.getOwnerManager().isTrusted(player, tile.getOwnType())) {
				if (!PlayerUtil.isOpped(player)) {
					PlayerUtil.sendTo(player, "ilib.no_permission");
					event.setCanceled(true);
				}
			}
		}

		IBlockState state = event.getWorld().getBlockState(pos);
		Block block = state.getBlock();

		if (block instanceof MUnbreakable) {
			if (player.getHeldItemMainhand().getItem() instanceof ItemBlock) {
				if (((ItemBlock) player.getHeldItemMainhand().getItem()).getBlock() == block) return;
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

	@SideOnly(Side.SERVER)
	@SubscribeEvent
	public static void dynamicViewDistance(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || Config.dynamicViewDistance < 1) return;

		if (checkTimer < 20 * 30) {
			checkTimer++;
			return;
		}
		checkTimer = 0;

		if (playerList.getCurrentPlayerCount() == 0) return;

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
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(event.getEntity().posX, event.getEntity().posY - 0.5, event.getEntity().posZ);
			IBlockState state = event.getWorld().getBlockState(pos);
			if (!state.isFullCube() && state.getCollisionBoundingBox(event.getWorld(), pos) != Block.NULL_AABB) event.setResult(Event.Result.DENY);
			pos.release();
		}
	}

}
