package ilib.misc;

import ilib.ClientProxy;
import ilib.asm.util.MCHooksClient;
import ilib.misc.ps.Cheat;
import ilib.util.ChatColor;
import ilib.util.PlayerUtil;
import roj.collect.MyHashSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

/**
 * @author solo6975
 * @since 2022/3/31 23:42
 */
public class XRay extends Cheat {
	public static boolean enable;
	static float originGamma;
	static MyHashSet<Block> target = new MyHashSet<>();

	public static boolean shouldCulled(IBlockState state) {
		return enable && !target.contains(state.getBlock());
	}

	public static void toggle() {
		GameSettings set = ClientProxy.mc.gameSettings;
		if (enable = !enable) {
			originGamma = set.gammaSetting;
			set.gammaSetting = 100;
		} else {
			set.gammaSetting = originGamma;
		}
		MCHooksClient.debugRenderAllSide = enable;

		ClientProxy.mc.renderGlobal.loadRenderers();
		PlayerUtil.sendTo(ClientProxy.mc.player, ChatColor.GREY + "XRay状态: " + ChatColor.ORANGE + enable);
	}

	@Override
	public void onCommand(EntityPlayerSP player, String[] args) {
		switch (args[1]) {
			case "add":
				for (int i = 2; i < args.length; i++) {
					Block block = Block.REGISTRY.getObject(new ResourceLocation(args[i]));
					if (block.getRegistryName().getPath().equals("air")) continue;

					target.add(block);
					PlayerUtil.sendTo(player, ChatColor.GREY + "已添加: " + ChatColor.ORANGE + block);
				}
				break;
			case "remove":
				for (int i = 2; i < args.length; i++) {
					Block block = Block.REGISTRY.getObject(new ResourceLocation(args[i]));
					if (block.getRegistryName().getPath().equals("air")) continue;

					if (target.remove(block)) PlayerUtil.sendTo(player, ChatColor.GREY + "已删除: " + ChatColor.ORANGE + block);
				}
				break;
		}
	}

	@Override
	public String toString() {
		return "透视方块, <add/remove> [block resource location]";
	}
}
