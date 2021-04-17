package ilib.asm.nx.mock;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ilib.net.mock.MockingUtil;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.ServerStatusResponse;

import net.minecraftforge.fml.client.ExtendedServerListData;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * @author solo6975
 * @since 2022/4/2 5:00
 */
@Nixim("/")
class MockDisc extends FMLClientHandler {
	@Shadow("/")
	private Minecraft client;
	@Shadow("/")
	private Map<ServerStatusResponse, JsonObject> extraServerListData;
	@Shadow("/")
	private Map<ServerData, ExtendedServerListData> serverDataTag;
	@Shadow("/")
	private static CountDownLatch startupConnectionData;

	@Inject("/")
	public void bindServerListData(ServerData data, ServerStatusResponse response) {
		JsonObject json = extraServerListData.get(response);
		if (json != null) {
			String type = json.get("type").getAsString();
			JsonArray modDataArray = json.get("modList").getAsJsonArray();
			MyHashMap<String, String> modListMap = new MyHashMap<>();
			for (JsonElement obj : modDataArray) {
				JsonObject modObj = obj.getAsJsonObject();
				modListMap.put(modObj.get("modid").getAsString(), modObj.get("version").getAsString());
			}

			String modRejections = FMLNetworkHandler.checkModList(modListMap, Side.SERVER);
			serverDataTag.put(data, new ExtendedServerListData(type, modRejections == null, modListMap, false));
		} else {
			serverDataTag.put(data, new ExtendedServerListData("原版", false, ImmutableMap.of(), false));
		}

		startupConnectionData.countDown();
	}

	@Inject("/")
	public void connectToServer(GuiScreen guiMultiplayer, ServerData serverEntry) {
		ExtendedServerListData data = serverDataTag.get(serverEntry);
		if (MockingUtil.autoMockVersion) {
			if (data != null) {
				MockingUtil.mockMods.clear();
				for (Map.Entry<String, String> entry : data.modData.entrySet()) {
					MockingUtil.mockMods.add(new String[]{entry.getKey(), entry.getValue()});
				}
			}
		}

		showGuiScreen(new GuiConnecting(guiMultiplayer, client, serverEntry));
	}
}
