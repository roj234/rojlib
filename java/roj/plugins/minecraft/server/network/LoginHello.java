package roj.plugins.minecraft.server.network;

import roj.collect.IntMap;
import roj.concurrent.Promise;
import roj.concurrent.TaskPool;
import roj.crypt.CryptoFactory;
import roj.crypt.FeedbackCipher;
import roj.crypt.IvParameterSpecNC;
import roj.crypt.RCipherSpi;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.text.TextUtil;
import roj.ui.Text;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.UUID;

/**
 * @author Roj234
 * @since 2024/3/19 15:58
 */
public class LoginHello implements ChannelHandler {
	private static final byte READY = 0, KEY = 1, VERIFY = 2;
	private byte state;
	private int nonce;

	private PlayerConnection player;

	public LoginHello(String address, int port) {
		MinecraftServer.LOGGER.info("登录请求: {}:{}", address, port);
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		Packet p = (Packet) msg;
		DynByteBuf in = p.getData();

		switch (p.name) {
			case "LoginHello" -> {
				if (state != READY) throw new MinecraftException("protocol error");

				String name = in.readVarIntUTF(16);
				PlayerPublicKey key = in.readBoolean() ? new PlayerPublicKey(in) : null;
				UUID uuid = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;

				player = new PlayerConnection(name, uuid, key);
				player.ctx = ctx;
				nonce = MinecraftServer.INSTANCE.random.nextInt();
				state = KEY;

				MinecraftServer.LOGGER.info("玩家{}开始登陆", player);

				ByteList buf = IOUtil.getSharedByteBuf();
				byte[] peRsa = MinecraftServer.INSTANCE.rsaPublicKeyBytes;
				buf.putVarIntUTF("").putVarInt(peRsa.length).put(peRsa).putVarInt(4).putInt(nonce);
				ctx.channelWrite(new Packet("LoginHello", buf));
			}
			case "LoginKey" -> {
				if (state != KEY) throw new MinecraftException("protocol error");

				byte[] aesKey = in.readBytes(in.readVarInt());
				boolean noSalt = in.readBoolean();
				long salt = !noSalt ? in.readLong() : 0;
				byte[] signature = in.readBytes(in.readVarInt());

				PrivateKey serverPrivateKey = MinecraftServer.INSTANCE.rsa.getPrivate();

				PlayerPublicKey playerKey = player.getPlayerKey();
				if (noSalt != (playerKey == null)) throw new MinecraftException("Protocol error");

				String serverHash;
				try {
					Cipher rsa = Cipher.getInstance("RSA");
					rsa.init(RCipherSpi.DECRYPT_MODE, serverPrivateKey);

					boolean success;
					if (playerKey == null) {
						// legacy
						byte[] myNonce = rsa.doFinal(signature);
						success = IOUtil.getSharedByteBuf().putInt(nonce).equals(ByteList.wrap(myNonce));
					} else {
						Signature sign = Signature.getInstance("SHA256withRSA");
						sign.initVerify(playerKey.key());

						ByteList buf = IOUtil.getSharedByteBuf();
						sign.update(buf.putInt(nonce).putLong(salt).array(), 0, 12);
						success = sign.verify(signature);
					}
					if (!success) throw new MinecraftException("签名验证失败");

					aesKey = rsa.doFinal(aesKey);
					serverHash = TextUtil.bytes2hex(computeServerId("", MinecraftServer.INSTANCE.rsaPublicKeyBytes, aesKey));

					ctx.channel().readInactive();
					insertCipher(ctx.channel(), aesKey);
				} catch (GeneralSecurityException e) {
					throw new MinecraftException("", e);
				}

				state = VERIFY;

				authenticateUser(MinecraftServer.INSTANCE, player, serverHash, ctx.remoteAddress() instanceof InetSocketAddress addr ? addr.getAddress() : null).then((v, cb) -> {
					ctx.channel().lock().lock();
					try {
						Text reject = MinecraftServer.INSTANCE.preLogin(ctx, player);
						if (reject != null) {
							player.disconnect(reject);
						} else {
							ctx.replaceSelf(player);
							ctx.channelOpened();
							ctx.channel().readActive();
							MinecraftServer.LOGGER.info("玩家{}成功登陆", player);
						}
					} catch (IOException e) {
						player.disconnect(e.getMessage());
					} finally {
						ctx.channel().lock().unlock();
					}
				}).rejected(exc -> {
					MinecraftServer.INSTANCE.getLogger().error("Promise Failure", exc);
					player.disconnect(exc instanceof MinecraftException me ? me.sendToPlayer : exc.toString());
					return IntMap.UNDEFINED;
				});
			}
			case "QueryRequest" -> throw new MinecraftException("该方法已不受支持");
		}
	}

	private Promise<Void> authenticateUser(MinecraftServer server, PlayerConnection player, String hash, InetAddress address) {
		return Promise.async(TaskPool.Common(), callback -> {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {}
			System.out.println("我等了100ms!");

			player.setOffline();
			callback.resolve(null);
		});
	}

	static void insertCipher(MyChannel channel, byte[] aesKey) throws IOException {
		try {
			var aes = CryptoFactory.AES();
			aes.init(RCipherSpi.ENCRYPT_MODE, aesKey); // avoid extra compute

			var encrypt = new FeedbackCipher(aes, FeedbackCipher.MODE_CFB);
			encrypt.init(RCipherSpi.ENCRYPT_MODE, aesKey, new IvParameterSpecNC(aesKey), null);
			var decrypt = new FeedbackCipher(aes, FeedbackCipher.MODE_CFB);
			decrypt.init(RCipherSpi.DECRYPT_MODE, aesKey, new IvParameterSpecNC(aesKey), null);

			channel.addBefore("splitter", "cipher", new CipherWrapper(encrypt, decrypt, true));
		} catch (GeneralSecurityException e) {
			throw new IOException("密码器初始化失败", e);
		}
	}

	public static byte[] computeServerId(String nonce, byte[] pubKey, byte[] aesKey) throws GeneralSecurityException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.update(nonce.getBytes(StandardCharsets.ISO_8859_1));
		md.update(aesKey);
		md.update(pubKey);
		return md.digest();
	}
}