package ilib.net.mock;

import ilib.ClientProxy;
import ilib.net.mock.adapter.RuleBasedPacketAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.text.TextUtil;

import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/3/31 21:50
 */
public class MockingUtil {
	public static SimpleList<Interceptor> modInterceptor = new SimpleList<>();

	public static boolean interceptPacket(FMLProxyPacket packet, INetHandler h) {
		for (int i = 0; i < modInterceptor.size(); i++) {
			if (modInterceptor.get(i).intercept(packet, h)) return true;
		}
		return false;
	}

	public static int mockProtocol = 340;
	public static boolean mockFMLMarker = true;
	public static String mockIp;
	public static int mockPort = -1;
	public static List<String[]> mockMods = new SimpleList<>();

	public static boolean autoMockVersion;
	public static IntMap<PacketAdapterFactory> registeredAdapters = new IntMap<>();

	public static void registerAdapter() {
		registeredAdapters.putInt(getVersionNumberFromMCString("1.16.5"), RuleBasedPacketAdapter::new);
	}

	public static void loadModList() {
		mockMods.clear();

		List<ModContainer> cs = Loader.instance().getActiveModList();
		for (int i = 0; i < cs.size(); i++) {
			ModContainer mod = cs.get(i);
			mockMods.add(new String[] {mod.getModId(), mod.getVersion()});
		}
	}

	private static ToIntMap<String> versionNumbers;

	public static int getVersionNumberFromMCString(String version) {
		if (versionNumbers == null) {
			versionNumbers = new ToIntMap<>();
			try {
				SimpleList<String> tmp = new SimpleList<>(5);
				for (String s : new LineReader(IOUtil.readUTF("META-INF/version.map"), true)) {
					if (s.startsWith("#")) continue;
					tmp.clear();
					TextUtil.split(tmp, s, '\t');
					versionNumbers.putInt(tmp.get(0), Integer.parseInt(tmp.get(1), tmp.get(1).startsWith("0x") ? 16 : 10));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return versionNumbers.getOrDefault(version, -1);
	}

	//
	public static final File baseDir = new File("packet_adapter");

	public static PacketAdapter getPacketAdapter() {
		PacketAdapterFactory factory = registeredAdapters.get(mockProtocol);
		if (factory != null) return factory.newAdapter();

		File file = new File(baseDir, mockProtocol + ".bin");
		if (!file.isFile()) return null;
		try {
			return new RuleBasedPacketAdapter().deserialize(IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream(file)));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void sendProxyPacket(String channel, PacketBuffer payload) {
		ClientProxy.mc.player.connection.getNetworkManager().sendPacket(new CPacketCustomPayload(channel, payload));
	}

	public static PacketBuffer newBuffer(int size) {
		return new PacketBuffer(Unpooled.buffer(128));
	}

	public static int readVarInt(ByteBuf in) {
		int varInt = 0;
		int i = 0;

		byte b1;
		do {
			b1 = in.readByte();
			varInt |= (b1 & 0x7F) << (i++ * 7);
			if (i > 5) {
				throw new DecoderException("varint too large");
			}

			if (!in.isReadable()) {
				in.resetReaderIndex();
				return -1;
			}
		} while ((b1 & 0x80) != 0);

		return varInt;
	}

	public interface Interceptor {
		boolean intercept(FMLProxyPacket packet, INetHandler handler);
	}
}
