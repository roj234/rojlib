package ilib.asm.util;

import ilib.client.RenderUtils;
import ilib.util.TimeUtil;
import roj.collect.ToIntMap;

import net.minecraft.block.BlockLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import static ilib.ClientProxy.mc;

/**
 * @author Roj233
 * @since 2022/5/23 17:48
 */
public class RecordCutHook {
	private static final ToIntMap<String> speeds = new ToIntMap<>();

	static {
		MinecraftForge.EVENT_BUS.register(RecordCutHook.class);
	}

	@SubscribeEvent
	public static void onClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		ItemStack stack = event.getItemStack();
		if (stack.getItem().getRegistryName().getPath().startsWith("record")) {
			if (stack.getItem().getMaxItemUseDuration(stack) > 0) {
				EntityPlayer player = event.getEntityPlayer();
				player.setActiveHand(event.getHand());
				speeds.putInt(player.getName(), 0);
			}
		}
	}

	@SubscribeEvent
	public static void onTickPlayer(TickEvent.PlayerTickEvent event) {
		EntityPlayer player = event.player;
		ToIntMap.Entry<String> entry = speeds.getEntry(player.getName());
		if (entry == null) return;
		int xx = player.getItemInUseMaxCount();
		if (xx == 0) {entry.v = Math.max(0, entry.v - 2);} else entry.v = Math.min(xx, 100);
	}

	@SubscribeEvent
	public static void onBreakBlock(PlayerEvent.BreakSpeed event) {
		int v = speeds.getInt(event.getEntityPlayer().getName());
		if (v >= 0) {
			String tool = event.getState().getBlock().getHarvestTool(event.getState());
			if ("axe".equals(tool) || "pickaxe".equals(tool)) {
				event.setNewSpeed(event.getOriginalSpeed() * v / 33.3f);
			}
		}
	}

	@SubscribeEvent
	public static void onRender(RenderWorldLastEvent event) {
		Entity p = mc.getRenderManager().renderViewEntity;
		if (p == null) return;

		RayTraceResult r = mc.objectMouseOver;
		if (r != null && r.typeOfHit == RayTraceResult.Type.BLOCK) {
			IBlockState state = mc.world.getBlockState(r.getBlockPos());
			if (state.getBlock() instanceof BlockLog) {
				ItemStack stack = mc.player.getHeldItem(EnumHand.MAIN_HAND);
				if (stack.getItem() instanceof ItemRecord) {
					double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * (double) event.getPartialTicks();
					double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * (double) event.getPartialTicks();
					double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * (double) event.getPartialTicks();

					GlStateManager.pushMatrix();

					Vec3d vec = r.hitVec;
					GlStateManager.translate(-x + vec.x, -y + vec.y, -z + vec.z);
					GlStateManager.scale(0.6f, 0.6f, 0.6f);

					GlStateManager.rotate(90, 1, 0, 0);

					float v = (float) Math.pow(1.1, speeds.getOrDefault(mc.player.getName(), 0) + event.getPartialTicks());
					GlStateManager.rotate(v * TimeUtil.tick % 360, 0, 0, 1);

					GlStateManager.scale(1, 1.9f, 1);

					RenderUtils.ITEM_RENDERER.renderItem(mc.player, stack, ItemCameraTransforms.TransformType.FIXED);

					GlStateManager.popMatrix();
				}
			}
		}
	}

	public static void init() {}

	public static void onChange(ItemStack from, ItemStack to) {
		//from.damageItem(new Random().nextInt(3), null);
	}
}
