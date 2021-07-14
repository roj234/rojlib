package lac.server;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.NetworkModHolder;
import net.minecraftforge.fml.relauncher.Side;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;

import javax.annotation.Nullable;
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
public class ModInfo {
    public static MyHashMap<String, int[]> modMinimum = new MyHashMap<>(),
                                            modOptional = new MyHashMap<>();
    public static MyHashSet<String> tmp = new MyHashSet<>();

    @Nullable
    public static String checkModList(Map<String, String> listData, Side side) {
        List<String> rejectStrings = new ArrayList<>();
        for (Map.Entry<ModContainer, NetworkModHolder> entry : NetworkRegistry.INSTANCE.registry().entrySet()) {
            String error = entry.getValue().checkCompatible(listData, side);
            if(error != null) {
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

    /**
     * 握手阶段先检测mod
     */
    public static boolean passHandshake(Map<String, String> mods) {
        tmp.clear();
        tmp.addAll(mods.keySet());
        for (String id : modMinimum.keySet()) {
            tmp.remove(id);
            // minimum不存在的话会被forge reject掉
        }
        if(!tmp.isEmpty()) {
            for (String id : modOptional.keySet()) {
                tmp.remove(id);
                // minimum不存在的话会被forge reject掉
            }
        }
        return tmp.isEmpty();
    }
}
