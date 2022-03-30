package ilib.misc.ps;

import ilib.command.sub.MySubs;
import ilib.misc.XRay;
import ilib.util.Colors;
import ilib.util.PlayerUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import roj.collect.MyHashMap;

/**
 * @author solo6975
 * @since 2022/3/31 23:39
 */
public abstract class Cheat {
    static MyHashMap<String, Cheat> childModules = new MyHashMap<>();

    public static MySubs getPrimaryCommand() {
        init();
        return new MySubs("ps") {
            public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if (args.length < 1) {
                    PlayerUtil.sendTo(sender, Colors.GREY + "PackageSimulator " + Colors.ORANGE + "1.0.0: " + Colors.WHITE + "随便使用数据包漏洞吧");
                    PlayerUtil.sendTo(sender, Colors.GREY + "当前注册的子模块: " + Colors.ORANGE + childModules);
                } else {
                    childModules.get(args[0]).onCommand((EntityPlayerSP) sender, args);
                }
            }
        };
    }

    private static void init() {
        childModules.put("waila_load", new WailaLoadChunk());
        childModules.put("xray", new XRay());
    }

    public abstract void onCommand(EntityPlayerSP player, String[] args);

    public abstract String toString();
}
