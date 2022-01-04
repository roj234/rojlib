package lac.server;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.BoxFile;
import roj.text.DottedStringPool;
import roj.text.StringPool;
import roj.util.ByteList;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.NetworkModHolder;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mod数据保存类
 *
 * @author Roj233
 * @since 2021/7/9 0:16
 */
public final class ModInfo {
    private static final MyHashMap<String, String[]>
            mods = new MyHashMap<>(), optional = new MyHashMap<>();
    public static String[] classListOrdered;

    @Nullable
    public static String checkModList(Map<String, String> listData, Side side) {
        List<String> rejectStrings = new ArrayList<>();
        for (Map.Entry<ModContainer, NetworkModHolder> entry : NetworkRegistry.INSTANCE.registry().entrySet()) {
            String error = entry.getValue().checkCompatible(listData, side);
            if (error != null) {
                rejectStrings.add(entry.getKey().getName() + ": " + error);
            }
        }
        if(rejectStrings.isEmpty())
            return null;

        rejectStrings.sort(String::compareTo);

        String rejectString = String.join("\n", rejectStrings);
        FMLLog.log.info("Mod不符合断开{}连接: {}", side, rejectString);
        return "Mod不符合:\n" + rejectString;
    }

    public static String makeFakeRejections(SocketAddress remote, Map<String, String> list) {
        Random rand = new Random(remote.toString().hashCode());
        List<String> rejectStrings = new ArrayList<>();
        for (Map.Entry<ModContainer, NetworkModHolder> entry : NetworkRegistry.INSTANCE.registry().entrySet()) {
            if(rand.nextFloat() > 0.8) {
                String error;
                switch (rand.nextInt(15)) {
                    case 0:
                    case 1:
                    case 2:
                        error = "Failed mod's custom NetworkCheckHandler " + entry.getKey();
                        break;
                    case 3:
                        error = "Error occurred invoking NetworkCheckHandler check at " + entry.getKey();
                        break;
                    default:
                        String fakeid = rand.nextInt(5) + "." + rand.nextInt(10) + "." + rand.nextInt(10);
                        error = "Requires " + fakeid + " but client has " + list.get(entry.getValue().getContainer().getModId());

                }
                rejectStrings.add(entry.getKey().getName() + ": " + error);
            }
        }

        return "Server Mod Rejection:\n" + (rejectStrings.isEmpty() ? "Error occurred invoking NetworkCheckHandler check at CodeChickenLib" : String.join("\n", rejectStrings));
    }

    private static final MyHashSet<String> tmp = new MyHashSet<>();
    /**
     * 检测mod
     */
    public static boolean check(Map<String, String> mods) {
        MyHashSet<String> tmp = ModInfo.tmp;
        tmp.clear();
        tmp.addAll(mods.keySet());
        for (String id : ModInfo.mods.keySet()) {
            if (tmp.remove(id) && notInArray(mods.get(id), ModInfo.mods.get(id)))
                return false;
            // minimum不存在的话会被forge reject掉
        }
        if(!tmp.isEmpty()) {
            for (String id : optional.keySet()) {
                if (tmp.remove(id) && notInArray(mods.get(id), ModInfo.mods.get(id)))
                    return false;
                // minimum不存在的话会被forge reject掉
            }
        }
        return tmp.isEmpty();
    }

    private static boolean notInArray(String s, String[] strings) {
        for (String string : strings) {
            if (s.equals(string)) return false;
        }
        return true;
    }

    public static void init(File f) throws IOException {
        BoxFile mod_info = new BoxFile(f);
        ByteList r = new ByteList(mod_info.getBytes("mods"));

        if (r.readInt() != (('M' << 24) | ('O' << 16) | ('D' << 8) | 'S'))
            throw new IOException("MODS header error");

        mods.clear();
        for (int i = r.readVarInt(false) - 1; i >= 0; i--) {
            String name = r.readVarIntUTF();
            int xl = r.readVarInt(false);
            String[] arr = new String[r.readVarInt(false)];
            for (int j = 0; j < arr.length; j++) {
                arr[j] = r.readVarIntUTF();
            }
            mods.put(name, arr);
        }

        optional.clear();
        for (int i = r.readVarInt(false) - 1; i >= 0; i--) {
            String name = r.readVarIntUTF();
            int xl = r.readVarInt(false);
            String[] arr = new String[r.readVarInt(false)];
            for (int j = 0; j < arr.length; j++) {
                arr[j] = r.readVarIntUTF();
            }
            optional.put(name, arr);
        }

        r.clear();
        mod_info.get("classes", r);

        if (r.readInt() != (('C' << 24) | ('L' << 16) | ('S' << 8) | 'S'))
            throw new IOException("CLSS header error");
        int a = r.readInt();
        StringPool sp = new DottedStringPool(r, '.');
        for (int i = 0; i < a; i++) {
            classListOrdered[i] = sp.readString(r);
        }
        mod_info.close();
    }
}
