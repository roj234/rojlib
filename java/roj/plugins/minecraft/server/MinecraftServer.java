package roj.plugins.minecraft.server;

import roj.asmx.event.EventBus;
import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.data.CMap;
import roj.config.serial.ToJson;
import roj.config.serial.ToSomeString;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.MyChannel;
import roj.net.ServerLaunch;
import roj.net.handler.Timeout;
import roj.net.handler.VarintSplitter;
import roj.plugin.Plugin;
import roj.plugins.minecraft.server.data.Block;
import roj.plugins.minecraft.server.data.Item;
import roj.plugins.minecraft.server.data.PlayerEntity;
import roj.plugins.minecraft.server.event.PlayerLoginEvent;
import roj.plugins.minecraft.server.network.FlowControl;
import roj.plugins.minecraft.server.network.PacketDecoder;
import roj.plugins.minecraft.server.network.PlayerConnection;
import roj.plugins.minecraft.server.network.PlayerInit;
import roj.text.TextWriter;
import roj.text.logging.Logger;
import roj.ui.AnsiString;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.HighResolutionTimer;
import roj.util.TypedKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj234
 * @since 2024/3/19 0019 15:04
 */
public class MinecraftServer extends Plugin {
	public static final TypedKey<PlayerConnection> PLAYER = new TypedKey<>("player");

	public static Logger LOGGER;
	public static MinecraftServer INSTANCE;

	public KeyPair rsa;
	public byte[] rsaPublicKeyBytes;

	public SecureRandom random;

	private final EventBus eventBus = new EventBus();
	private ServerLaunch listener;

	public EventBus getEventBus() { return eventBus; }

	@Override
	protected void onEnable() throws Exception {
		HighResolutionTimer.activate();
		LOGGER = getLogger();
		INSTANCE = this;

		setMeta(JSONParser.parses("""
				{
					"version": {
						"protocol": 760,
						"name": "1.19.2"
					},
					"players": {
						"online": 114,
						"max": 514
					},
					"previewsChat": false,
					"enforcesSecureChat": false,
					"preventsChatReports": true,
					"description": {
						"text": "\\u00a7c史\\u00a76上\\u00a7e最\\u00a7a牛\\u00a7b验\\u00a79证\\u00a7d码\\n\\u00a7b\\u00a7ldoge"
					}
				}""").asMap());

		getLogger().info("代理版本: 1.19.2 注册的方块: "+ Block.STATE_ID.nextId()+" 注册的物品: "+ Item.byId.length);

		getLogger().info("正在生成密钥对");
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(512);
		this.rsa = kpg.genKeyPair();
		this.rsaPublicKeyBytes = rsa.getPublic().getEncoded();
		this.random = SecureRandom.getInstanceStrong();

		int port = 25565;
		getLogger().info("正在监听0.0.0.0:"+port);

		listener = ServerLaunch.tcp().bind(port).initializator(ch -> {
			LOGGER.info("来自{}的连接", ch.remoteAddress());
			ch.addLast("flow_control", new FlowControl())
			  .addLast("splitter", VarintSplitter.twoMbVI())
			  .addLast("timeout", new Timeout(30000,500))
			  .addLast("packet", new PacketDecoder());
			connection.put(ch, true);
		}).launch();
	}

	@Override
	protected void onDisable() {
		getLogger().info("正在停止监听");
		try {
			listener.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		getLogger().info("正在停止服务");
		for (PlayerConnection value : players.values()) {
			value.disconnect("服务器已关闭");
		}
		for (MyChannel ch : connection.keySet()) {
			try {
				ch.closeGracefully();
			} catch (IOException e) {
				try {
					ch.close();
				} catch (IOException ex) {
					LOGGER.warn("error", ex);
				}
			}
		}
	}

	public final ConcurrentHashMap<MyChannel, Boolean> connection = new ConcurrentHashMap<>();
	public final ConcurrentHashMap<UUID, PlayerConnection> players = new ConcurrentHashMap<>();

	public PlayerConnection getPlayer(UUID uuid) { return players.get(uuid); }
	public Collection<PlayerConnection> getPlayers() { return new SimpleList<>(players.values()); }

	private final CMap meta = new CMap();
	private volatile DynByteBuf metaBytes;
	public void setMeta(CMap meta) {
		metaBytes = null;
		this.meta.merge(meta, false, true);
	}
	public DynByteBuf getMetaBytes() {
		block:
		if (metaBytes == null) {
			synchronized (this) {
				if (metaBytes != null) break block;

				metaBytes = new ByteList();
				ByteList buf = IOUtil.getSharedByteBuf();

				try (TextWriter tw = new TextWriter(buf, StandardCharsets.UTF_8)) {
					ToSomeString ser = new ToJson().sb(tw);
					meta.accept(ser);
					ser.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

				metaBytes.putVarInt(buf.readableBytes()).put(buf);
			}
		}
		return metaBytes.slice();
	}

	public AnsiString preLogin(ChannelCtx ctx, PlayerConnection connection) {
		ctx.channel().addLast("create_world", new PlayerInit(connection));

		PlayerLoginEvent.Pre event = new PlayerLoginEvent.Pre(connection, ctx.channel());
		if (eventBus.post(event)) {
			AnsiString message = event.getMessage();
			return message == null ? new AnsiString("你被拒绝加入服务器") : message;
		}
		return null;
	}

	public PlayerEntity postLogin(PlayerConnection connection) {
		PlayerEntity entity = new PlayerEntity();
		entity.uuid = connection.getUUID();

		PlayerLoginEvent.Post event = new PlayerLoginEvent.Post(connection, entity);
		eventBus.post(event);
		return event.getPlayerEntity();
	}

	public int getCompressionThreshold() {return 256;}
	public int getViewDistance() {return 12;}
}