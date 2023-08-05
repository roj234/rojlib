package roj.misc;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.YAMLParser;
import roj.config.data.CMapping;
import roj.crypt.ILProvider;
import roj.crypt.KeyType;
import roj.io.IOUtil;
import roj.math.MutableLong;
import roj.net.NetworkUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.ClientLaunch;
import roj.net.ch.ServerLaunch;
import roj.net.ch.handler.Compress;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.mss.JPrivateKey;
import roj.net.mss.SimpleEngineFactory;
import roj.net.proto_obf.ProtoObf;
import roj.text.ACalendar;
import roj.text.logging.Logger;
import roj.text.logging.LoggingStream;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2022/4/26 22:59
 * @deprecated WorldRepo will replace it
 * @see roj.minecraft.worlddiff.WorldRepo
 */
public class FileSync implements ChannelHandler {
	static File baseDir;
	static MyHashMap<String, byte[]> hashes = new MyHashMap<>();
	static long timestamp;
	private static int HASH_LENGTH;

	public static void main(String[] args) throws Exception {
		LoggingStream log = new LoggingStream(Logger.getLogger());
		System.setOut(log);
		System.setErr(log);

		InputStream cfgin = FileSync.class.getClassLoader().getResourceAsStream("cas_config.yml");
		if (cfgin == null) {
			if (args.length == 0) throw new IOException("缺少配置文件.");
			cfgin = new FileInputStream(args[0]);
		}

		CMapping config = new YAMLParser().parseRaw(cfgin).asMap();

		List<String> subDirs = Helpers.cast(config.getOrCreateList("dir").unwrap());
		File base = new File(config.getString("base_dir", ".")).getAbsoluteFile();
		if (!base.isDirectory()) throw new IllegalArgumentException("路径不存在（不是文件夹）");

		baseDir = base;
		String basePath = base.getAbsolutePath();
		int prefixLength = basePath.length();
		if (!basePath.endsWith("/") && !basePath.endsWith("\\")) prefixLength++;
		int javac傻逼 = prefixLength;

		System.out.println("正在计算MD5...");

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		HASH_LENGTH = md5.getDigestLength();

		MutableLong lastMod = new MutableLong(0);
		for (String dirname : subDirs) {
			File dir = new File(base, dirname);

			IOUtil.findAllFiles(dir, file -> {
				long t = file.lastModified();
				if (lastMod.value < t) lastMod.value = t;

				byte[] buf = IOUtil.getSharedByteBuf().list;
				try (FileInputStream in = new FileInputStream(file)) {
					md5.reset();

					while (true) {
						int r = in.read(buf);
						if (r < 0) break;
						md5.update(buf,0,r);
					}
					hashes.put(file.getAbsolutePath().substring(javac傻逼).replace('\\', '/'), md5.digest());
				} catch (Exception e) {
					System.err.println("文件"+file+"无法读取");
					e.printStackTrace();
				}

				return false;
			});
		}
		timestamp = lastMod.value;
		System.out.println("找到 "+hashes.size()+" 个文件, 最新的在 " + ACalendar.toLocalTimeString(timestamp) + " 修改");

		boolean tls = config.getBool("security");
		boolean obf = config.getBool("obfuscate");

		if (config.getBool("server")) {
			System.out.println("生成EdDSA证书");

			ILProvider.register();
			KeyPair kp = KeyType.getInstance("EdDSA").getKeyPair(new File("fs.key"), new File("fs.pem"), "114514".getBytes());

			SimpleEngineFactory factory = SimpleEngineFactory.server().key(new JPrivateKey(kp));

			InetSocketAddress addr = NetworkUtil.getListenAddress(config.getString("address"));

			System.out.println("服务器已启动");
			ServerLaunch.tcp("FileSync").initializator(ch -> {
				ch.addLast("DDOS", new AntiDDoSHelper());
				if (obf) ProtoObf.install(ch, factory.get());
				else if (tls) ch.addLast("MSS", new MSSCipher(factory.get()));
				ch.addLast("Splitter", VarintSplitter.twoMbVLUI())
				  .addLast("Compress", new Compress())
				  .addLast("Timeout", new Timeout(10000,2000))
				  .addLast("Handler", new FileSync());
			}).listen(addr).launch();
		} else {
			System.out.println("运行在客户端模式");
			Client.allow_extension = config.getOrCreateList("allow_extensions").asStringSet();
			if (Client.allow_extension.isEmpty()) {
				Client.allow_extension = new MyHashSet<>("jar", "zip", "zs", "toml", "json", "yml", "yaml", "cfg", "txt");
			}

			InetSocketAddress addr = NetworkUtil.getConnectAddress(config.getString("address"));
			ClientLaunch.tcp("CAS").initializator(ch -> {
				if (obf) ProtoObf.install(ch);
				else if (tls) ch.addLast("MSS", new MSSCipher());
				ch.addLast("Splitter", VarintSplitter.twoMbVLUI())
				  .addLast("Compress", new Compress())
				  .addLast("Timeout", new Timeout(10000,2000))
				  .addLast("Handler", new Client());
			}).connect(addr).daemon(false).launch();
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = ((DynByteBuf) msg);
		int i = buf.readUnsignedByte();
		if (i != 114) {
			System.err.println("无效的数据包,"+i);
			ctx.close();
		}

		long client_timestamp = buf.readLong();

		ByteList out = IOUtil.getSharedByteBuf().put(1);

		System.out.println("收到客户端数据 " + ctx.remoteAddress() + ", len="+buf.readableBytes());
		int 多=0,改=0;

		MyHashSet<String> set = new MyHashSet<>(hashes.keySet());
		while (buf.isReadable()) {
			int pos = buf.rIndex;
			String name = buf.readVUIUTF();

			set.remove(name);
			byte[] hash = hashes.get(name);
			if (hash == null) {
				out.put(buf, pos, buf.rIndex-pos).put(1);
				多++;
			} else if (!new ByteList(hash).equals(buf.slice(HASH_LENGTH))) {
				out.put(buf, pos, buf.rIndex-HASH_LENGTH-pos).put(0);
				改++;

				File file = new File(baseDir, name);
				out.putVUInt((int) file.length());
				out.readStreamFully(new FileInputStream(file)).put(hash);
			} else {
				continue;
			}

			if (out.readableBytes() > 32767) {
				ctx.channelWrite(out);
				out.clear();
				out.put(1);
			}
		}
		System.out.println("比对完成:多"+多+",改"+改+",缺"+set.size());

		for (String name : set) {
			out.putVUIUTF(name).put(0);

			File file = new File(baseDir, name);
			out.putVUInt((int) file.length());
			out.readStreamFully(new FileInputStream(file))
			   .put(hashes.get(name));

			if (out.readableBytes() > 32767) {
				ctx.channelWrite(out);
				out.clear();
				out.put(1);
			}
		}

		if (out.readableBytes() > 1) ctx.channelWrite(out);

		out.clear();
		out.put(2).putLong(timestamp);
		ctx.channelWrite(out);

		System.out.println("结束会话");

		ctx.flush();
		ctx.channel().closeGracefully();
	}

	public static class AntiDDoSHelper implements ChannelHandler {
		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			// 你请求了两次数据, 这可能是因为服务器的BUG, 也可能是你想DDOS服务器"
			ctx.channelOpened();
		}
	}

	public static class Client implements ChannelHandler {
		static boolean dirty, success;
		static int pkt = 0;
		static MyHashSet<String> allow_extension;

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			ByteList out = IOUtil.getSharedByteBuf();
			out.put(114).putLong(timestamp);
			for (Map.Entry<String, byte[]> entry : hashes.entrySet()) {
				out.putVUIUTF(entry.getKey()).put(entry.getValue());
			}
			ctx.channelWrite(out);
			System.out.println("已发送数据");
			success = true;

			ctx.channelOpened();
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			System.out.println(success ? "已断开与服务器的连接" : "连接超时");
			System.exit(success ? 0 : 1);
		}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DynByteBuf buf = ((DynByteBuf) msg);

			int type = buf.readUnsignedByte();
			if (type == 0) {
				System.err.println("服务器错误: " + buf.readUTF(buf.readableBytes()));
				ctx.close();
				return;
			}

			// end packet
			if (type == 2) {
				long serverTime = buf.readLong();
				System.out.println("服务器在 " + ACalendar.toLocalTimeString(serverTime) + " 修改");
				ctx.channel().closeGracefully();
				return;
			}

			System.out.println("接收第" + pkt++ + "个数据包");
			int 删=0,改=0;
			// type 1, continue data
			while (buf.isReadable()) {
				String path = buf.readVUIUTF();

				if (!IOUtil.safePath(path).equals(path)) throw new IOException("服务器返回的路径不安全: " + path);
				if (!allow_extension.contains(IOUtil.extensionName(path))) throw new IOException("服务器返回的扩展名不安全: " + path);

				File target = new File(baseDir, path);

				int action = buf.readUnsignedByte();
				if (action == 1) {
					if (target.delete()) {
						删++;
						hashes.remove(path);
						IOUtil.removeEmptyPaths(Collections.singletonList(target.getAbsolutePath()));
						dirty = true;
					} else {
						System.err.println("文件 " + path + " 删除失败");
					}
					continue;
				}

				byte[] data = buf.readBytes(buf.readVUInt());
				byte[] hash = buf.readBytes(HASH_LENGTH);

				target.getParentFile().mkdirs();
				try (FileOutputStream out = new FileOutputStream(target)) {
					out.write(data);

					改++;
					// update hash if write successful
					hashes.put(path,hash);
					dirty = true;
				} catch (IOException e) {
					System.err.println("文件 " + path + " 更新失败");
					e.printStackTrace();
				}
			}
			System.out.println("处理完毕,删除了"+删+"个文件,修改了"+改+"个文件");
		}
	}
}
