package ilib.event;

import ilib.ImpLib;
import ilib.net.IMessage;
import ilib.net.IMessageHandler;
import ilib.net.MessageContext;
import ilib.net.MyChannel;
import ilib.util.MCTexts;
import ilib.util.TimeUtil;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.ToLongMap;
import roj.io.FileUtil;
import roj.util.ByteList;
import roj.util.ByteList.Streamed;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/30 22:59
 */
public class Sync {
	static ByteList NULL_BYTES = new Streamed();
	static MyChannel SYNC = new MyChannel("IL_SYN");

	ToLongMap<String> fileMD5 = new ToLongMap<>();
	MyHashMap<String, ByteList> fileData = new MyHashMap<>();

	FileMD5 packetMD5;
	String scriptDirectory;

	public Sync(File dir) {
		if (!dir.isDirectory()) throw new IllegalArgumentException("Not a directory");

		scriptDirectory = dir.getAbsolutePath();
		List<File> scriptFiles = FileUtil.findAllFiles(dir);
		for (File file : scriptFiles) {
			try (FileInputStream stream = new FileInputStream(file)) {
				fileData.put(file.getAbsolutePath(), new ByteList().readStreamFully(stream));
			} catch (IOException e) {
				throw new RuntimeException("配置读取失败", e);
			}
		}

		ByteList md5 = new ByteList(16);
		md5.wIndex(16);
		for (Map.Entry<String, ByteList> entry : fileData.entrySet()) {
			ByteList bl = entry.getValue();
			FileUtil.MD5.update(bl.list, 0, bl.wIndex());
			FileUtil.MD5.digest(md5.list);
			FileUtil.MD5.reset();
			md5.rIndex = 0;
			fileMD5.put(entry.getKey(), md5.readLong() ^ md5.readLong());
		}

		ImpLib.logger().info("File syncer registered for " + dir);

		if (ImpLib.isClient) return;
		MinecraftForge.EVENT_BUS.register(this);
		SYNC.registerMessage(packetMD5 = new FileMD5(this), FileMD5.class, 1, Side.CLIENT);
		SYNC.registerMessage(new RequestFile(this), RequestFile.class, 2, null);
	}

	MyHashSet<EntityPlayer> replied = new MyHashSet<>();

	@SubscribeEvent
	public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
		SYNC.sendTo(packetMD5, (EntityPlayerMP) event.player);
	}

	@SubscribeEvent
	public void onPlayerLeft(PlayerEvent.PlayerLoggedOutEvent event) {
		replied.remove(event.player);
	}

	boolean isPathSafe(String pathName) {
		return pathName.startsWith(scriptDirectory);
	}

	public static class FileMD5 implements IMessage, IMessageHandler<FileMD5> {
		Sync owner;

		FileMD5(Sync owner) {
			this.owner = owner;
			serverMD5 = owner.fileMD5;
		}

		public FileMD5() {
			serverMD5 = new ToLongMap<>();
		}

		ToLongMap<String> serverMD5;

		@Override
		public void fromBytes(PacketBuffer buf) {
			int len = buf.readVarInt();
			for (int i = 0; i < len; i++) {
				serverMD5.put(buf.readString(64), buf.readLong());
			}
		}

		@Override
		public void toBytes(PacketBuffer buf) {
			buf.writeVarInt(serverMD5.size());
			for (ToLongMap.Entry<String> entry : serverMD5.selfEntrySet()) {
				buf.writeString(entry.k).writeLong(entry.v);
			}
		}

		@Override
		public void onMessage(FileMD5 message, MessageContext ctx) {
			MyHashMap<String, ByteList> update = new MyHashMap<>();

			for (ToLongMap.Entry<String> entry : serverMD5.selfEntrySet()) {
				Long wrap = owner.fileMD5.remove(entry.k);
				if (wrap == null || wrap != entry.v) {
					update.put(entry.k, null);
				}
			}
			if (!owner.fileMD5.isEmpty()) {
				for (ToLongMap.Entry<String> entry : owner.fileMD5.selfEntrySet()) {
					if (!new File(entry.k).delete()) {
						TimeUtil.beginText.add(MCTexts.format("mi.sync.ioerror") + "无法删除 " + entry.k);
					}
				}
				TimeUtil.beginText.add(MCTexts.format("ilib.sync.remove") + owner.fileMD5.keySet());
			}
			owner.fileMD5 = serverMD5;

			if (!update.isEmpty()) {
				SYNC.sendToServer(new RequestFile(update));
			}
		}
	}

	public static class RequestFile implements IMessage, IMessageHandler<RequestFile> {
		Sync owner;

		RequestFile(Sync owner) {
			this.owner = owner;
		}

		boolean fromClient;
		MyHashMap<String, ByteList> files;

		public RequestFile() {
			files = new MyHashMap<>();
		}

		public RequestFile(MyHashMap<String, ByteList> files) {
			this.files = files;
			fromClient = true;
		}

		@Override
		public void fromBytes(PacketBuffer buf) {
			int len = buf.readVarInt();
			if (buf.readBoolean()) { // fromClient
				for (int i = 0; i < len; i++) {
					String key = buf.readString(64);
					files.put(key, null);
				}
				fromClient = false;
			} else {
				for (int i = 0; i < len; i++) {
					String key = buf.readString(64);
					byte[] data = new byte[buf.readVarInt()];
					buf.readBytes(data);
					files.put(key, new ByteList(data));
				}
			}
		}

		@Override
		public void toBytes(PacketBuffer buf) {
			buf.writeVarInt(files.size()).writeBoolean(fromClient);
			if (fromClient) {
				for (String key : files.keySet()) {
					buf.writeString(key);
				}
			} else {
				for (Map.Entry<String, ByteList> entry : files.entrySet()) {
					buf.writeString(entry.getKey()).writeVarInt(entry.getValue().wIndex()).writeBytes(entry.getValue().list, entry.getValue().arrayOffset(), entry.getValue().wIndex());
				}
			}
		}

		@Override
		public void onMessage(RequestFile msg, MessageContext ctx) {
			if (ctx.side == Side.CLIENT) {
				if (!msg.files.isEmpty()) {
					for (Map.Entry<String, ByteList> entry : msg.files.entrySet()) {
						File file = new File(entry.getKey());
						if (!owner.isPathSafe(file.getAbsolutePath())) {
							TimeUtil.beginText.add("\u00a7c警告: 服务端尝试写入不合法的文件(" + file.getAbsolutePath() + "), 这可能是一个病毒, 也可能只是服务器配置错误. However, 请提高警惕性.");
							return;
						} else {
							try (FileOutputStream fos = new FileOutputStream(file)) {
								entry.getValue().writeToStream(fos);
							} catch (IOException e) {
								TimeUtil.beginText.add(MCTexts.format("ilib.sync.ioerror") + e.getMessage());
								ImpLib.logger().warn(e);
							}
						}
					}
					TimeUtil.beginText.add(MCTexts.format("ilib.sync.synced") + files.keySet());
				} else {
					TimeUtil.beginText.add(MCTexts.format("ilib.sync.same"));
				}
			} else {
				if (owner.replied.add(ctx.getPlayer())) {
					for (Map.Entry<String, ByteList> entry : msg.files.entrySet()) {
						entry.setValue(owner.fileData.getOrDefault(entry.getKey(), NULL_BYTES));
					}
					SYNC.sendTo(msg, (EntityPlayerMP) ctx.getPlayer());
				} else {
					ctx.getServerHandler().disconnect(new TextComponentString("[ImpLib配置同步系统] \u00a7c你请求了两次数据, 这可能是因为服务器的BUG, 也可能是你想DDOS服务器"));
				}
			}
		}
	}
}
