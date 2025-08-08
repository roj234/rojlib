package roj.concurrent;

import roj.reflect.Bypass;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author Roj234
 * @since 2023/3/5 2:39
 */
interface AQSHelper {
	AQSHelper INSTANCE = Bypass.builder(AQSHelper.class).delegate(AbstractQueuedSynchronizer.class, "apparentlyFirstQueuedIsExclusive").build();

	boolean apparentlyFirstQueuedIsExclusive(AbstractQueuedSynchronizer aqs);
}