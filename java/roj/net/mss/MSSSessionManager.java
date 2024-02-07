package roj.net.mss;

import roj.collect.CharMap;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/11/3 0003 15:56
 */
public interface MSSSessionManager {
	MSSSession getOrCreateSession(CharMap<DynByteBuf> extIn, DynByteBuf id);
}
