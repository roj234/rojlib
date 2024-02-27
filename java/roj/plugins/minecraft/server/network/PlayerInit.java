package roj.plugins.minecraft.server.network;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.plugins.minecraft.server.util.TranslatedString;
import roj.plugins.minecraft.server.util.Utils;
import roj.ui.AnsiString;
import roj.ui.Terminal;
import roj.util.ByteList;

import java.io.IOException;

import static roj.plugins.minecraft.server.data.Enums.GAMEMODE_SURVIVAL;

/**
 * @author Roj234
 * @since 2024/3/19 0019 23:12
 */
public class PlayerInit implements ChannelHandler {
	private static final byte[] NBT = Utils.constantize("assets/DynamicRegistry.json");
	private final PlayerConnection player;

	public PlayerInit(PlayerConnection player) {
		this.player = player;
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		ByteList buf = IOUtil.getSharedByteBuf();

		int entityId = player.getEntity().id;
		buf.clear();
		ctx.channelWrite(new Packet("GameJoin", buf
			.putInt(entityId) // playerEntityId
			.putBool(false) // hardcore
			.put(GAMEMODE_SURVIVAL) // ubyte[GAMEMODE] gameMode
			.put(GAMEMODE_SURVIVAL) // ubyte[GAMEMODE] previousGameMode
			.putVarInt(1).putVarIntUTF("pmcs:overworld") // collection dimensionIds
			.put(NBT) // optional[NBT] dynamicRegistryManager
			.putVarIntUTF("custom:pmcs_overworld") // identifier dimensionType
			.putVarIntUTF("pmcs:overworld") // identifier dimensionId
			.putLong(0x1234567890L) // sha256Seed
			.putVarInt(1) // maxPlayers
			.putVarInt(MinecraftServer.INSTANCE.getViewDistance()) // viewDistance
			.putVarInt(4) // simulationDistance
			.putBool(false) // reducedDebugInfo
			.putBool(false) // showDeathScreen
			.putBool(false) // debugWorld
			.putBool(false) // flatWorld
			.putBool(false) // optional[GlobalPos] lastDeathLocation
			/*$$0.writeOptional(this.lastDeathLocation, PacketByteBuf::writeGlobalPos);*/
		));

		// identifier channel
		// opaque data
		buf.clear();
		ctx.channelWrite(new Packet("CustomPayload", buf.putVarIntUTF("minecraft:brand").putVarIntUTF("[IL]PMCS")));

		// ubyte[Difficulty] difficulty
		// boolean difficultyLocked
		ctx.channelWrite(new ConstantPacket("Difficulty", "0001"));

		// bitset[invulnerable, flying, allowFlying, creativeMode]
		// float flySpeed, walkSpeed
		buf.clear();
		ctx.channelWrite(new Packet("PlayerAbilities", buf.put(0xF).putFloat(0.2f).putFloat(0.05f)));

		writeCommonInit(ctx);

		buf.clear();
		ctx.channelWrite(new Packet("PlayerSpawn", player.getEntity().createSpawnPacket(buf)));

		// int id
		// byte status
		buf.clear();
		ctx.channelWrite(new Packet("EntityStatus", buf.putInt(entityId).put(0x18)));

		//EntityTrackerUpdate
		//EntityAttributes
		//EntityEquipmentUpdate
		//EntitySetHeadYaw
		//HealthUpdate
		//ExperienceBarUpdate
		//AdvancementUpdate
		// [STDOUT]: packetId=0,data=ec07c8cdc9c5c8ba4bb184d2ad97843499a351c0473638596268c14028000000000000c014fee6ed0aab04007a66000000fd8d0000
		// [STDOUT]: packetId=80,data=ec070000000205000612000407000f00020e0a000c01000d0100080000090241a000000b07000a01000101ac02030700070100050700100700ff
		// [STDOUT]: packetId=104,data=ec0702206d696e6563726166743a67656e657269632e6d6f76656d656e745f73706565643fd0000000000000001c6d696e6563726166743a67656e657269632e6d61785f6865616c7468403400000000000000
		// [STDOUT]: packetId=83,data=ec070001ce05010a000003000644616d6167650000000000
		// [STDOUT]: packetId=63,data=ec0766

		buf.clear();
		ctx.channelWrite(new Packet("PlayerList", buf
			.put(0)
			.putVarInt(1)
			// GameProfile
			.putLong(player.getUUID().getMostSignificantBits()).putLong(player.getUUID().getLeastSignificantBits())
			.putVarIntUTF(player.getName())
			.putVarInt(0)
			// END GameProfile
			.putVarInt(1) // gamemode
			.putVarInt(player.getPing()) // ping
			.putBool(true).putVarIntUTF(new AnsiString("看我干啥，你不会以为这是真实的服务器吧！").toMinecraftJson()) // optional[Text] displayName
			.putBool(false) // optional[PublicKeyData] publicKey
		));

		// double centerX, centerZ, size
		// double sizeLerpTarget
		// varLong sizeLerpTime
		// varInt maxRadius
		// varInt warningBlocks, warningTime
		buf.clear();
		ctx.channelWrite(new Packet("WorldBorderInitialize", buf
			.putDouble(0).putDouble(0).putDouble(512)
			.putDouble(512).putVarLong(0)
			.putVarInt(512)
			.putVarInt(0).putVarInt(0)
		));

		// long time, timeOfDay
		buf.clear();
		long time = System.currentTimeMillis() / 50;
		ctx.channelWrite(new Packet("WorldTimeUpdate", buf
			.putLong(time).putLong(12000)
		));

		// Position pos
		// float angle (yaw)
		buf.clear();
		ctx.channelWrite(new Packet("PlayerSpawnPosition", buf
			.putLong(Utils.pos2long(0,32,0)).putFloat(90)
		));

		player.sendMessage(new TranslatedString("multiplayer.player.joined", new AnsiString(player.getName())).color16(Terminal.YELLOW+ Terminal.HIGHLIGHT), false);

		ctx.removeSelf();
		ctx.channelOpened();
	}

	private static void writeCommonInit(ChannelCtx ctx) throws IOException {
		// optional[text] description
		// optional[varIntUTF base64] favicon
		// boolean previewsChat, secureChatEnforced
		ctx.channelWrite(new ConstantPacket("ServerMetadata", "00 00 00 00"));

		// byte[range[0,9)] selectedSlot
		ctx.channelWrite(new ConstantPacket("UpdateSelectedSlot", "00"));

		// collection[recipe] recipes
		// identifier serializer, name, [unknown] payload => recipe
		ctx.channelWrite(new ConstantPacket("SynchronizeRecipes", "00"));

		// Map<Identifier, Map<Identifier, IntList>> tags;
		ctx.channelWrite(new ConstantPacket("SynchronizeTags", "00"));

		// byte[Action] action
		// [boolean, boolean][opened, filtering]
		// identifier[] recipeIdsToChange
		// identifier[] recipeIdsToInit
		ctx.channelWrite(new ConstantPacket("UnlockRecipes", "00 0000 0000 0000 0000 00 00"));

		// byte distance
		ctx.channelWrite(new ConstantPacket("ChunkLoadDistance", "08"));
		ctx.channelWrite(new ConstantPacket("SimulationDistance", "01"));

		// varInt x, z;
		ctx.channelWrite(new ConstantPacket("ChunkRenderDistanceCenter", "00 00"));
	}
}