package roj.plugins.minecraft.server.network;

import roj.collect.IntBiMap;
import roj.io.IOUtil;
import roj.io.BufferPool;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.handler.VarintSplitter;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/19 15:43
 */

public class PacketDecoder implements ChannelHandler {
	public static final int VERSION_CODE = 760;
	public static final IntBiMap<String>[] PACKET_ID = Helpers.cast(new IntBiMap<?>[8]);
	static {
		for (int i = 0; i < PACKET_ID.length; i++) {
			PACKET_ID[i] = new IntBiMap<>();
		}
	}
	static {
		// STATE, RECEIVER_IS_SERVER, NAME
		addPacket(-1, true, "Handshake");
		addPacket(0, true,
			"TeleportConfirm",
			"QueryBlockNbt",
			"UpdateDifficulty",
			"MessageAcknowledgment",
			"CommandExecution",
			"ChatMessage",
			"RequestChatPreview",
			"ClientStatus",
			"ClientSettings",
			"RequestCommandCompletions",
			"ButtonClick",
			"ClickSlot",
			"CloseHandledScreen",
			"CustomPayload",
			"BookUpdate",
			"QueryEntityNbt",
			"PlayerInteractEntity",
			"JigsawGenerating",
			"KeepAlive",
			"UpdateDifficultyLock",
			"PlayerMove.PositionAndOnGround",
			"PlayerMove.PosRot",
			"PlayerMove.Rot",
			"PlayerMove.StatusOnly",
			"VehicleMove",
			"BoatPaddleState",
			"PickFromInventory",
			"CraftRequest",
			"UpdatePlayerAbilities",
			"PlayerAction",
			"ClientCommand",
			"PlayerInput",
			"PlayPong",
			"RecipeCategoryOptions",
			"RecipeBookData",
			"RenameItem",
			"ResourcePackStatus",
			"AdvancementTab",
			"SelectMerchantTrade",
			"UpdateBeacon",
			"UpdateSelectedSlot",
			"UpdateCommandBlock",
			"UpdateCommandBlockMinecart",
			"CreativeInventoryAction",
			"UpdateJigsaw",
			"UpdateStructureBlock",
			"UpdateSign",
			"HandSwing",
			"SpectatorTeleport",
			"PlayerInteractBlock",
			"PlayerInteractItem");
		addPacket(0, false,
			"EntitySpawn",
			"ExperienceOrbSpawn",
			"PlayerSpawn",
			"EntityAnimation",
			"Statistics",
			"PlayerActionResponse",
			"BlockBreakingProgress",
			"BlockEntityUpdate",
			"BlockEvent",
			"BlockUpdate",
			"BossBar",
			"Difficulty",
			"ChatPreview",
			"ClearTitle",
			"CommandSuggestions",
			"CommandTree",
			"CloseScreen",
			"Inventory",
			"ScreenHandlerPropertyUpdate",
			"ScreenHandlerSlotUpdate",
			"CooldownUpdate",
			"ChatSuggestions",
			"CustomPayload",
			"PlaySoundId",
			"HideMessage",
			"Disconnect",
			"EntityStatus",
			"Explosion",
			"UnloadChunk",
			"GameStateChange",
			"OpenHorseScreen",
			"WorldBorderInitialize",
			"KeepAlive",
			"ChunkData",
			"WorldEvent",
			"Particle",
			"LightUpdate",
			"GameJoin",
			"MapUpdate",
			"SetTradeOffers",
			"Entity.MoveRelative",
			"Entity.PosRot",
			"Entity.Rot",
			"VehicleMove",
			"OpenWrittenBook",
			"OpenScreen",
			"SignEditorOpen",
			"PlayPing",
			"CraftFailedResponse",
			"PlayerAbilities",
			"MessageHeader",
			"ChatMessage",
			"EndCombat",
			"EnterCombat",
			"DeathMessage",
			"PlayerList",
			"LookAt",
			"PlayerPositionLook",
			"UnlockRecipes",
			"EntitiesDestroy",
			"RemoveEntityStatusEffect",
			"ResourcePackSend",
			"PlayerRespawn",
			"EntitySetHeadYaw",
			"ChunkDeltaUpdate",
			"SelectAdvancementTab",
			"ServerMetadata",
			"OverlayMessage",
			"WorldBorderCenterChanged",
			"WorldBorderInterpolateSize",
			"WorldBorderSizeChanged",
			"WorldBorderWarningTimeChanged",
			"WorldBorderWarningBlocksChanged",
			"SetCameraEntity",
			"UpdateSelectedSlot",
			"ChunkRenderDistanceCenter",
			"ChunkLoadDistance",
			"PlayerSpawnPosition",
			"ChatPreviewStateChange",
			"ScoreboardDisplay",
			"EntityTrackerUpdate",
			"EntityAttach",
			"EntityVelocityUpdate",
			"EntityEquipmentUpdate",
			"ExperienceBarUpdate",
			"HealthUpdate",
			"ScoreboardObjectiveUpdate",
			"EntityPassengersSet",
			"Team",
			"ScoreboardPlayerUpdate",
			"SimulationDistance",
			"Subtitle",
			"WorldTimeUpdate",
			"Title",
			"TitleFade",
			"PlaySoundFromEntity",
			"PlaySound",
			"StopSound",
			"GameMessage",
			"PlayerListHeader",
			"NbtQueryResponse",
			"ItemPickupAnimation",
			"EntityPosition",
			"AdvancementUpdate",
			"EntityAttributes",
			"EntityStatusEffect",
			"SynchronizeRecipes",
			"SynchronizeTags");
		addPacket(1, true, "QueryRequest", "QueryPing");
		addPacket(1, false, "QueryResponse", "QueryPong");
		addPacket(2, true, "LoginHello", "LoginKey", "QueryRequest");
		addPacket(2, false, "Disconnect", "LoginHello", "LoginSuccess", "LoginCompression", "QueryResponse");
	}

	private static void addPacket(int state, boolean receiverIsServer, String... names) {
		IntBiMap<String> map = PACKET_ID[mapId(state, receiverIsServer)];
		for (int i = 0; i < names.length; i++) {
			map.put(i, names[i]);
		}
	}

	private static int mapId(int state, boolean receiverIsServer) { return ((state + 1) << 1) + (receiverIsServer ? 0 : 1); }

	private static final int HANDSHAKE = 0, PLAY = 2, STATUS = 4, LOGIN = 6;
	private int state;

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException { state = HANDSHAKE; }

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		int id = buf.readVarInt();
		String name = PACKET_ID[state].get(id);
		if (name == null) {
			if (state != PLAY) {
				ctx.close();
				return;
			}
		} else if (state == HANDSHAKE) {
			try {
				int protocolVersion = buf.readVarInt();
				String address = buf.readVarIntUTF(255);
				int port = buf.readUnsignedShort();
				int state = buf.readVarInt();

				if (state == 2) {
					this.state = LOGIN;
					if (protocolVersion != VERSION_CODE) {
						ByteList buf1 = IOUtil.getSharedByteBuf();
						channelWrite(ctx, new Packet("Disconnect", buf1
							.putVarIntUTF(protocolVersion < VERSION_CODE ? "客户端版本过期，请使用1.19.2" : "服务器版本过期，请使用1.19.2")
						));
						ctx.channel().closeGracefully();
						return;
					}
					ctx.channel().addLast("play", new LoginHello(address, port));
				} else if (state == 1) {
					this.state = STATUS;
					ctx.channel().addLast("ping", new StatusPinger());
				} else {
					ctx.close();
					return;
				}

				ctx.channelOpened();
				return;
			} catch (Exception e) {
				e.printStackTrace();
				ctx.close();
			}
		}

		ctx.channelRead(new Packet(name, buf));
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		var p = (Packet) msg;
		int id = PACKET_ID[state|1].getByValueOrDefault(p.name, -1);
		if (id < 0) throw new IOException("未知的数据包"+p.name);

		if (p instanceof ConstantPacket cp) {
			var cdata = cp.getConstantData();
			if (cdata != null) {
				ChannelCtx ctx1 = ctx.channel().handler("cipher");
				ctx1.handler().channelWrite(ctx1, cdata);
				return;
			} else {
				ctx.channel().addAfter("cipher", "constant_capture", cp);
			}
		}

		var data = p.getData();
		var buf = ctx.alloc().expandBefore(data, VarintSplitter.getVarIntLength(id));
		try {
			int pos = buf.wIndex();
			buf.wIndex(0);
			buf.putVarInt(id).wIndex(pos);
			ctx.channelWrite(buf);
		} finally {
			if (buf != data) BufferPool.reserve(buf);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		MinecraftServer.INSTANCE.connection.remove(ctx.channel());
	}

	public void startPlay() { state = PLAY; }
}