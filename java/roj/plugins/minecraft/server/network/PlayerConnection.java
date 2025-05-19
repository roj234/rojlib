package roj.plugins.minecraft.server.network;

import org.jetbrains.annotations.Range;
import roj.asmx.event.EventBus;
import roj.concurrent.ScheduleTask;
import roj.concurrent.Scheduler;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.math.Vec3d;
import roj.math.Vec3i;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.plugins.minecraft.server.data.EnumFacing;
import roj.plugins.minecraft.server.data.Enums;
import roj.plugins.minecraft.server.data.ItemStack;
import roj.plugins.minecraft.server.data.PlayerEntity;
import roj.plugins.minecraft.server.event.CommandEvent;
import roj.plugins.minecraft.server.event.CustomPayloadEvent;
import roj.plugins.minecraft.server.event.PlayerLoginEvent;
import roj.plugins.minecraft.server.event.PlayerMoveEvent;
import roj.plugins.minecraft.server.util.TranslatedString;
import roj.plugins.minecraft.server.util.Utils;
import roj.ui.Text;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Roj234
 * @since 2024/3/19 15:11
 */
public class PlayerConnection implements ChannelHandler/*, ViaCommandSender*/ {
	private final String name;
	private UUID uuid;
	private final PlayerPublicKey playerKey;
	private PlayerEntity entity;

	ChannelCtx ctx;

	private ScheduleTask keepaliveTask, tickTask;
	private boolean sendKeepAilve;
	private long pingTime;
	private int ping;

	public PlayerConnection(String name, UUID uuid, PlayerPublicKey playerKey) {
		this.name = name;
		this.uuid = uuid;
		this.playerKey = playerKey;
	}

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		this.ctx = ctx; // has been set previous

		ByteList buf = IOUtil.getSharedByteBuf();

		try {
			MinecraftServer server = MinecraftServer.INSTANCE;
			if (server.getCompressionThreshold() >= 0) {
				buf.putVarInt(server.getCompressionThreshold());
				ctx.channelWrite(new Packet("LoginCompression", buf));
				ctx.channel().addBefore("packet", "compress", new Compress(server.getCompressionThreshold()));
			}

			buf.clear();
			ctx.channelWrite(new Packet("LoginSuccess", buf
				.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
				.putVarIntUTF(name)
				.putVarInt(0)
			));

			((PacketDecoder) ctx.channel().handler("packet").handler()).startPlay();

			entity = server.postLogin(this);
			Scheduler scheduler = MinecraftServer.INSTANCE.getScheduler();
			keepaliveTask = scheduler.loop(() -> {
				if (sendKeepAilve) {
					disconnect(new TranslatedString("disconnect.timeout"));
				} else {
					sendKeepAilve = true;
					pingTime = System.currentTimeMillis();
					ctx.channel().fireChannelWrite(new Packet("KeepAlive", IOUtil.getSharedByteBuf().putLong(pingTime)));
				}
			}, 15000, -1, 15000);
			tickTask = scheduler.loop(this::tick, 50);

			ctx.attachment(MinecraftServer.PLAYER, this);
		} catch (IOException e) {
			e.printStackTrace();
			disconnect(e.getMessage());
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		Packet p = (Packet) msg;
		DynByteBuf in = p.getData();
		switch (p.name) {
			default -> {
				ctx.channelRead(p);
				in.rIndex = in.wIndex();
			}
			case "CommandExecution" -> {
				//$$0.writeString(this.command, 256);
				//$$0.writeInstant(this.timestamp);
				//$$0.writeLong(this.salt);
				//this.argumentSignatures.write($$0);
				//$$0.writeBoolean(this.signedPreview);
				//this.acknowledgment.write($$0);
				String command = in.readVarIntUTF(256);
				long time = in.readLong();
				EventBus bus = MinecraftServer.INSTANCE.getEventBus();
				if (bus.hasListener(CommandEvent.class)) {
					bus.post(new CommandEvent(this, command, time));
				} else {
					MinecraftServer.LOGGER.warn("{}执行了指令/{}", this, command);
				}
				in.rIndex = in.wIndex();
			}
			case "CustomPayload" -> {
				EventBus bus = MinecraftServer.INSTANCE.getEventBus();
				if (bus.hasListener(CustomPayloadEvent.class)) {
					bus.post(new CustomPayloadEvent(in));
				}
				in.rIndex = in.wIndex();
			}
			case "KeepAlive" -> {
				if (sendKeepAilve && in.readLong() == pingTime) {
					int i = (int)(System.currentTimeMillis() - pingTime);
					this.ping = (this.ping * 3 + i) / 4;
					sendKeepAilve = false;

					ByteList buf = IOUtil.getSharedByteBuf();
					ctx.channelWrite(new Packet("PlayerList", buf
						.put(2)
						.putVarInt(1)
						.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
						.putVarInt(ping)
					));
				} else {
					disconnect(new TranslatedString("disconnect.timeout"));
				}
			}
			case "TeleportConfirm" -> {
				int id = in.readVarInt();
				synchronized (this) {
					if (id == this.teleportId) {
						if (teleportPos == null) {
							disconnect(new TranslatedString("multiplayer.disconnect.invalid_player_movement"));
							return;
						}

						entity.x = teleportPos.x;
						entity.y = teleportPos.y;
						entity.z = teleportPos.z;
						moveMask = 0;
						teleportPos = null;
						teleportId = -1;
					}
				}
			}
			case "PlayerMove.StatusOnly" -> {
				synchronized (this) {
					moveMask |= 1;
					moveOnGround = in.readBoolean();
					if (++receivedMove > 10) disconnect("移动数据包过于频繁 (10/T)");
				}
			}
			case "PlayerMove.PositionAndOnGround" -> {
				synchronized (this) {
					moveMask |= 3;
					moveX = in.readDouble();
					moveY = in.readDouble();
					moveZ = in.readDouble();
					moveOnGround = in.readBoolean();
					if (++receivedMove > 10) disconnect("移动数据包过于频繁 (10/T)");
				}
			}
			case "PlayerMove.Rot" -> {
				synchronized (this) {
					moveMask |= 5;
					moveYaw = in.readFloat();
					movePitch = in.readFloat();
					moveOnGround = in.readBoolean();
					if (++receivedMove > 10) disconnect("移动数据包过于频繁 (10/T)");
				}
			}
			case "PlayerMove.PosRot" -> {
				synchronized (this) {
					moveMask |= 7;
					moveX = in.readDouble();
					moveY = in.readDouble();
					moveZ = in.readDouble();
					moveYaw = in.readFloat();
					movePitch = in.readFloat();
					moveOnGround = in.readBoolean();
					if (++receivedMove > 10) disconnect("移动数据包过于频繁 (10/T)");
				}
			}
			case "ClientSettings" -> {
				setting = new ClientSettings(
					in.readVarIntUTF(16),
					in.readByte(),
					in.readByte(),
					in.readBoolean(),
					in.readByte(),
					in.readByte(),
					in.readBoolean(),
					in.readBoolean()
				);
			}
			case "CreativeInventoryAction" -> {
				if (entity.isCreative) {
					entity.inventory[in.readUnsignedShort()] = new ItemStack(in);
				} else {
					in.rIndex = in.wIndex();
				}
			}
			case "UpdateSelectedSlot" -> {
				entity.selectedInventory = 36 + in.readShort();
			}
			case "HandSwing" -> {
				isBreaking = true;
				breakOrPlace = in.readBoolean();
			}
			case "PlayerAction" -> {
				int action = in.readUnsignedByte();
				Vec3i pos = Utils.readBlockPos(in);
				EnumFacing facing = EnumFacing.VALUES[in.readUnsignedByte()];
				int seq = in.readVarInt();

				System.out.println(pos);
				switch (action) {
					case Enums.START_DESTROY_BLOCK:
					case Enums.ABORT_DESTROY_BLOCK:
					case Enums.STOP_DESTROY_BLOCK:
				}
			}
		}
	}

	private volatile int receivedMove;

	private Vec3i breakPos;
	private boolean isBreaking;
	private boolean breakOrPlace;
	private int breakProgress;

	private volatile byte moveMask;
	private double moveX, moveY, moveZ;
	private float moveYaw, movePitch;
	private boolean moveOnGround;

	private int tick;

	private int teleportTick;
	private int teleportId;
	private Vec3d teleportPos;
	private void tick() throws IOException {
		tickNaturalMove();
		tickMove();
		tickBreakBlock();
		tick++;
	}
	private void tickMove() throws IOException {
		byte mask;
		double x, y, z;
		float yaw, pitch;
		boolean onGround;

		Vec3d tpPos;

		PlayerEntity player = entity;
		synchronized (this) {
			receivedMove = 0;

			mask = moveMask;
			if (mask == 0) return;

			if ((mask & 2) == 0) {
				x = player.x;
				y = player.y;
				z = player.z;
			} else {
				x = moveX;
				y = moveY;
				z = moveZ;
			}

			if ((mask & 4) == 0) {
				yaw = player.yaw;
				pitch = player.pitch;
			} else {
				yaw = moveYaw;
				pitch = movePitch;
			}

			onGround = (mask&1) != 0 ? player.onGround : moveOnGround;

			moveMask = 0;

			tpPos = teleportPos;
		}

		if (x != x | y != y | z != z | yaw != yaw | pitch != pitch) {
			disconnect(new TranslatedString("multiplayer.disconnect.invalid_player_movement"));
			return;
		}

		x = MathUtils.clamp(x, -3E7, 3E7);
		y = MathUtils.clamp(y, -3E7, 3E7);
		z = MathUtils.clamp(z, -3E7, 3E7);
		yaw = yaw % 180F;
		pitch = pitch % 180F;

		willNotMove:
		if (tpPos != null) {
			if (tick - teleportTick > 20) {
				teleportTick = tick;
				teleport(tpPos.x, tpPos.y, tpPos.z, player.yaw, player.pitch);
			}
			return;
		} else {
			double moveDistance = 0;
			if ((mask&2) != 0) {
				double sq = (x-player.x);
				moveDistance += sq*sq;
				sq = (y-player.y);
				moveDistance += sq*sq;
				sq = (z-player.z);
				moveDistance += sq*sq;
			}

			double maxDistance = player.moveDisabled ? 1 : player.flying ? 300 : 100;
			if (moveDistance > maxDistance) break willNotMove;

			boolean jump = player.onGround && !onGround;

			EventBus bus = MinecraftServer.INSTANCE.getEventBus();
			if (bus.hasListener(PlayerMoveEvent.class)) {
				PlayerMoveEvent event = new PlayerMoveEvent(player, x, y, z, yaw, pitch, jump, moveDistance);
				if (bus.post(event)) break willNotMove;
				x = event.x;
				y = event.y;
				z = event.z;
				yaw = event.yaw;
				pitch = event.pitch;
				jump = event.jump;
			}

			if (!player.collisionDisabled && checkCollision(player, x, y, z)) break willNotMove;

			if (jump) player.jump();
			player.moveTo(x, y, z, yaw, pitch);
			return;
		}

		teleport(player.x, player.y, player.z, player.yaw, player.pitch);
	}
	private void tickNaturalMove() throws IOException {

	}
	private void tickBreakBlock() {
		if (breakPos == null) return;
		if (breakOrPlace) {
			breakOrPlace = false;
			breakProgress++;
		}
	}
	private boolean checkCollision(PlayerEntity player, double x, double y, double z) {
		return false;
	}

	public void teleport(double x, double y, double z, float yaw, float pitch) {
		teleportPos = new Vec3d(x, y, z);
		if (++teleportId == Integer.MAX_VALUE) teleportId = 0;
		teleportTick = tick;

		// double x,y,z
		// float yaw,pitch
		// bitset[X, Y, Z, Y_ROT, X_ROT] relativePositions
		// varint teleportId
		// bool shouldDismount
		ByteList buf = IOUtil.getSharedByteBuf();
		sendPacket(new Packet("PlayerPositionLook", buf
			.putDouble(x).putDouble(y).putDouble(z)
			.putFloat(yaw).putFloat(pitch)
			.put(0)
			.putVarInt(teleportId)
			.putBool(false)
		));
	}

	public ClientSettings getSetting() { return setting; }
	private ClientSettings setting;
	public record ClientSettings(String language, byte viewDistance, byte charVisibility, boolean charColors, byte playerModelBitMask, byte mainAri, boolean filterText, boolean allowsListing) {}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		MinecraftServer.INSTANCE.getEventBus().post(new PlayerLoginEvent.Disconnect(this, 0));
		if (keepaliveTask != null) keepaliveTask.cancel();
		if (tickTask != null) tickTask.cancel();
		MinecraftServer.LOGGER.info("玩家{}退出了服务器", this);
	}

	public boolean isOnline() { return false; }
	void setOffline() {
		uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
	}

	public PlayerPublicKey getPlayerKey() { return playerKey; }
	public String getName() { return name; }
	public UUID getUUID() { return uuid; }
	public PlayerEntity getEntity() { return entity; }

	public void sendMessage(String s) {
		sendPacket(new Packet("GameMessage", IOUtil.getSharedByteBuf().putVarIntUTF(simpleJsonMessage(s)).putBool(false)));
	}
	public void sendMessage(Text message, boolean overlay) {
		sendPacket(new Packet("GameMessage", IOUtil.getSharedByteBuf().putVarIntUTF(message.toMinecraftJson()).putBool(overlay)));
	}
	public void playGlobalSound(String soundId, byte category, @Range(from = 0, to = 2) float pitch) {
		ByteList buf = IOUtil.getSharedByteBuf();
		sendPacket(new Packet("PlaySoundId", buf
			.putVarIntUTF(soundId)
			.put(category)
			.putInt((int) entity.x)
			.putInt((int) entity.y)
			.putInt((int) entity.z)
			.putFloat(100F)
			.putFloat(pitch)
			.putLong(System.currentTimeMillis())
		));
	}

	public void disconnect(String message) { sendDisconnect(simpleJsonMessage(message)); }
	public void disconnect(Text message) { sendDisconnect(message.toMinecraftJson()); }
	private static String simpleJsonMessage(String message) { return "{\"text\":\"" + Tokenizer.escape(message) + "\"}"; }
	private void sendDisconnect(CharSequence message) {
		if (!connection().isOutputOpen()) return;
		sendPacket(new Packet("Disconnect", IOUtil.getSharedByteBuf().putVarIntUTF(message)));
		try {
			connection().closeGracefully();
		} catch (IOException ignored) {}
	}

	public void sendPacket(Packet packet) {
		try {
			connection().fireChannelWrite(packet);
		} catch (IOException e) {
			try {
				ctx.close();
			} catch (IOException ex) {}
			if (e instanceof AsynchronousCloseException) return;

			MinecraftServer.LOGGER.warn("数据包发送时出现异常", e);
		}
	}

	//@Override
	public boolean hasPermission(String s) { return false; }

	@Override
	public String toString() { return name+" ("+uuid+(playerKey!=null?") key="+playerKey:")"); }

	public int getPing() { return ping; }
	public MyChannel connection() { return ctx.channel(); }

	public void syncInventory() {
		ByteList buf = IOUtil.getSharedByteBuf();
		// byte syncId
		// varInt revision
		// list [ItemStack] contents
		// ItemStack cursorStack

		buf.clear();
		buf.put(0).putVarInt(tick);
		ItemStack[] inv = entity.inventory;
		buf.putVarInt(inv.length);
		for (int i = 0; i < inv.length; i++) inv[i].toMinecraftPacket(buf);
		entity.cursorStack.toMinecraftPacket(buf);

		sendPacket(new Packet("Inventory", buf));
	}
}