// 天天造轮子系列
package ilib.net;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.util.DimensionHelper;
import ilib.util.PlayerUtil;
import roj.collect.MyHashMap;
import roj.reflect.DirectAccessor;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketDisconnect;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;

public class MyChannel {
	static final MyHashMap<String, MyChannel> CHANNELS = new MyHashMap<>(2, 0.9f);

	final ChannelCodec serverCodec, clientCodec;

	/**
	 * 注册一个{@link ProxyPacket}数据包处理系统
	 *
	 * @param channel 频道名
	 */
	public MyChannel(String channel) {
		serverCodec = new ChannelCodec(Side.SERVER, channel);
		clientCodec = new ChannelCodec(Side.CLIENT, channel);
		CHANNELS.put(channel, this);
	}

	@SuppressWarnings("unchecked")
	public <M extends IMessage> void registerMessage(IMessageHandler<M> handler, Class<M> message, int id, Side receiver) {
		Supplier<M> supplier = (Supplier<M>) DirectAccessor.builder(Supplier.class).constructFuzzy(message, "get").build();
		registerMessage(handler, message, supplier, id, receiver);
	}

	public <M extends IMessage> void registerMessage(IMessageHandler<M> handler, Class<M> message, Supplier<M> newMessage, int id, Side receiver) {
		if (receiver == Side.SERVER) {
			clientCodec.addEnc(id, message);
			serverCodec.addDec(id, newMessage, handler);
		} else {
			serverCodec.addEnc(id, message);
			if (ImpLib.isClient) clientCodec.addDec(id, newMessage, handler);
			if (receiver == null) {
				clientCodec.addEnc(id, message);
				serverCodec.addDec(id, newMessage, handler);
			}
		}
	}

	/**
	 * 发送到玩家
	 */
	public void sendTo(@Nonnull IMessage message, @Nonnull EntityPlayerMP player) {
		NetHandlerPlayServer conn = player.connection;

		if (conn == null) {
			ImpLib.logger().warn(player.getName() + " already disconnected.");
			return;
		}
		if (conn.netManager.channel().attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY).get() != EnumConnectionState.PLAY) {
			ImpLib.logger().warn(player.getName() + " is not playing.", new Throwable());
			return;
		}

		conn.sendPacket(serverCodec.encode(message));
	}

	/**
	 * 发送给所有
	 */
	public void sendToAll(@Nonnull IMessage message) {
		List<EntityPlayerMP> players = PlayerUtil.getOnlinePlayers();
		for (int i = 0; i < players.size(); i++) {
			sendTo(message, players.get(i));
		}
	}

	/**
	 * 发送给周围
	 */
	public void sendToAllAround(@Nonnull IMessage message, @Nonnull World world, int x, int y, int z, double radius) {
		double dx = x + 0.5d, dy = y + 0.5d, dz = z + 0.5d;
		List<EntityPlayer> players = world.playerEntities;
		for (int i = 0; i < players.size(); i++) {
			EntityPlayer player = players.get(i);
			if (player.getDistanceSq(dx, dy, dz) < radius) {
				sendTo(message, (EntityPlayerMP) player);
			}
		}
	}

	/**
	 * 发送给周围
	 */
	public void sendToAllAround(@Nonnull IMessage message, int dimension, int x, int y, int z, double radius) {
		World world = DimensionHelper.getWorldForDimension(null, dimension);
		if (world instanceof WorldServer) {
			sendToAllAround(message, world, x, y, z, radius);
			return;
		}
		double dx = x + 0.5d, dy = y + 0.5d, dz = z + 0.5d;
		List<EntityPlayerMP> players = PlayerUtil.getOnlinePlayers();
		for (int i = 0; i < players.size(); i++) {
			EntityPlayerMP player = players.get(i);
			if (player.world.provider.getDimension() == dimension && player.getDistanceSq(dx, dy, dz) < radius) {
				sendTo(message, player);
			}
		}
	}

	/**
	 * 发送到邻近的chunk, 一般来说，你可以使用{@link #sendToAllTrackingChunk(IMessage, int, int, int)}
	 * C: TileEntity center chunk
	 * N: Nearby chunk (Of course no one's hand can longer than a chunk)
	 * =====-=====-=====
	 * | N | | N | | N |
	 * =====-=====-=====
	 * | N | | C | | N |
	 * =====-=====-=====
	 * | N | | N | | N |
	 * =====-=====-=====
	 */
	public void sendToAllTrackingNearbyChunk(@Nonnull IMessage message, int dimension, int x, int z) {
		World world = DimensionHelper.getWorldForDimension(null, dimension);
		if (world != null) {
			sendToAllTrackingNearbyChunk(message, world, x, z);
		}
	}

	/**
	 * 发送到邻近的chunk, 一般来说，你可以使用{@link #sendToAllTrackingChunk(IMessage, World, int, int)}
	 * C: TileEntity center chunk
	 * N: Nearby chunk (Of course no one's hand can longer than a chunk)
	 * =====-=====-=====
	 * | N | | N | | N |
	 * =====-=====-=====
	 * | N | | C | | N |
	 * =====-=====-=====
	 * | N | | N | | N |
	 * =====-=====-=====
	 */
	public void sendToAllTrackingNearbyChunk(@Nonnull IMessage message, @Nonnull World world, int x, int z) {
		for (int dx = -1; dx < 2; dx++) {
			for (int dz = -1; dz < 2; dz++) {
				sendToAllTrackingChunk(message, world, x + dx, z + dz);
			}
		}
	}

	public void sendToAllTrackingChunk(@Nonnull IMessage message, int dimension, int x, int z) {
		World world = DimensionHelper.getWorldForDimension(null, dimension);
		if (world != null) {
			sendToAllTrackingChunk(message, world, x, z);
		}
	}

	public void sendToAllTrackingChunk(@Nonnull IMessage message, @Nonnull World world, int x, int z) {
		List<EntityPlayerMP> chunk = PlayerUtil.getAllPlayersWatchingChunk(world, x, z);
		for (int i = 0; i < chunk.size(); i++) {
			EntityPlayerMP player = chunk.get(i);
			sendTo(message, player);
		}
	}

	public void sendToAllTracking(@Nonnull IMessage message, Entity entity) {
		if (!ImpLib.proxy.isOnThread(false)) {
			ImpLib.proxy.runAtMainThread(false, () -> sendToAllTracking(message, entity));
		} else {
			((WorldServer) entity.getEntityWorld()).getEntityTracker().sendToTracking(entity, serverCodec.encode(message));
		}
	}

	public void sendToDimension(@Nonnull IMessage message, int dimensionId) {
		List<EntityPlayerMP> dimension = PlayerUtil.getAllPlayersInDimension(dimensionId);
		for (int i = 0; i < dimension.size(); i++) {
			EntityPlayerMP player = dimension.get(i);
			sendTo(message, player);
		}
	}

	@SideOnly(Side.CLIENT)
	public void sendToServer(@Nonnull IMessage message) {
		EntityPlayerSP player = ClientProxy.mc.player;
		if (player != null) {
			player.connection.getNetworkManager().sendPacket(clientCodec.encode(message));
		}
	}

	public static void kickWithMessage(EntityPlayerMP player, String text) {
		kickWithMessage(player.connection, new TextComponentString(text));
	}

	public static void kickWithMessage(EntityPlayerMP player, ITextComponent text) {
		kickWithMessage(player.connection, text);
	}

	public static void kickWithMessage(INetHandler handler, String text) {
		kickWithMessage(handler, new TextComponentString(text));
	}

	@SuppressWarnings("unchecked")
	public static void kickWithMessage(INetHandler handler, ITextComponent text) {
		NetworkManager man;
		if (handler instanceof NetHandlerPlayServer) {
			(man = ((NetHandlerPlayServer) handler).getNetworkManager()).sendPacket(new SPacketDisconnect(text), result -> man.closeChannel(text));
		} else {
			(man = ((NetHandlerPlayClient) handler).getNetworkManager()).closeChannel(text);
		}
		man.channel().config().setAutoRead(false);
	}

}