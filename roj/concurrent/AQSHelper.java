package roj.concurrent;

import roj.reflect.DirectAccessor;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author Roj234
 * @since 2023/3/5 0005 2:39
 */
interface AQSHelper {
	AQSHelper INSTANCE = DirectAccessor.builder(AQSHelper.class).delegate(AbstractQueuedSynchronizer.class, "apparentlyFirstQueuedIsExclusive").build();

	boolean apparentlyFirstQueuedIsExclusive(AbstractQueuedSynchronizer aqs);
}
