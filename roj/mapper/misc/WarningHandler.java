package roj.mapper.misc;

import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.CharMap;
import roj.mapper.ConstMapper;
import roj.mapper.util.AccessFallback;
import roj.mapper.util.Desc;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/1/24 1:40
 */
public final class WarningHandler implements AccessFallback {
    public WarningHandler() {}

    @Override
    public boolean setFlag(Desc desc, CharMap<FlagList> interner) {
        return false;
    }

    @Override
    public void handleUnmatched(Map<String, Map<String, Desc>> rest, CharMap<FlagList> interner) {
        if (!rest.isEmpty()) {
            System.out.println("[CM-Warn] 缺少元素: ");
            for (Map.Entry<String, Map<String, Desc>> entry : rest.entrySet()) {
                System.out.print(entry.getKey());
                System.out.print(": ");
                Iterator<Desc> itr = entry.getValue().values().iterator();
                while (true) {
                    Desc desc = itr.next();
                    System.out.print(desc.name);
                    if (!desc.param.isEmpty()) {
                        System.out.print(' ');
                        System.out.print(desc.param);
                    }
                    if (!itr.hasNext()) {
                        System.out.println();
                        break;
                    }
                    System.out.print("  ");
                }
            }
            FlagList PUBLIC = interner.computeIfAbsent(AccessFlag.PUBLIC, ConstMapper.fl);
            for (Map<String, Desc> map : rest.values()) {
                for (Desc desc : map.values()) { // 将没有flag的全部填充为public
                    desc.flags = PUBLIC;
                }
            }
        }
    }
}
