package roj.sql;

import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.Label;
import roj.concurrent.SegmentReadWriteLock;
import roj.io.buf.LeakDetector;
import roj.reflect.FastInit;
import roj.reflect.Proxy;
import roj.util.Helpers;

import java.nio.channels.ClosedByInterruptException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/5/17 0017 9:20
 */
public class SimpleConnectionPool {
	final DbConnector connector;

	private final SegmentReadWriteLock lock = new SegmentReadWriteLock();

	private final Connection[] pool;
	private final long[] stale;

	private final LeakDetector ld = LeakDetector.create();

	private volatile int multiplier;

	private final LinkedTransferQueue<Object[]> transfer = new LinkedTransferQueue<>();

	static final ThreadLocal<Object[]> connId = new ThreadLocal<>();

	public SimpleConnectionPool(DbConnector connector, int pooledConnections) {
		this.connector = connector;
		if (pooledConnections <= 0 || pooledConnections > 32) throw new IllegalArgumentException("仅支持1-32个连接");
		this.pool = new Connection[pooledConnections];
		this.stale = new long[pooledConnections];
		this.multiplier = 0; // 先乐观一点
	}

	public SQLException removeStale(long timeout) {
		long maxBefore = System.currentTimeMillis()-timeout;

		SQLException e1 = null;
		for (int i = 0; i < stale.length; i++) {
			if (stale[i] < maxBefore) {
				lock.tryLock(i);
				try {
					if (stale[i] < maxBefore) {
						Connection conn = pool[i];
						if (conn != null) {
							try {
								conn.close();
							} catch (SQLException e) {
								if (e1 == null) e1 = e;
								else e1.addSuppressed(e);
							}
						}
						pool[i] = null;
					}
				} finally {
					lock.unlock(i);
				}
			}
		}
		return e1;
	}

	public Connection getConnection(long timeoutMs) throws SQLException {
		if (connId.get() != null) throw new SQLException("当前线程已经获取了连接");

		int id = (pool.length-1) & ((int) Thread.currentThread().getId() * multiplier);
		long deadline = System.currentTimeMillis()+timeoutMs;

		// optimistic lock
		if (!lock.tryLock(id)) {
			try {
				while (true) {
					id = (id+1)&(pool.length-1);
					if (lock.tryLock(id)) break;

					Object[] conn = transfer.poll(10, TimeUnit.MICROSECONDS);
					if (conn != null) {
						connId.set(conn);
						stale[(int) conn[0]] = System.currentTimeMillis();
						return wrap((Connection) conn[1]);
					}

					if (timeoutMs != 0 && System.currentTimeMillis() > deadline) return null;
				}
			} catch (InterruptedException e) {
				Helpers.athrow(new ClosedByInterruptException());
				return null;
			}
		}

		if (pool[id] == null) pool[id] = connector.createConnection();

		connId.set(new Object[]{id, pool[id]});
		stale[id] = System.currentTimeMillis();
		return wrap(pool[id]);
	}

	private static volatile Function<Object[], Connection> proxy;
	private Connection wrap(Connection connection) {
		if (proxy == null) {
			block:
			synchronized (this) {
				if (proxy != null) break block;
				ConstantData data = new ConstantData();
				data.name("roj/sql/SimpleConnectionPool$PooledConnection");

				int closeHandler = data.newField(0, "$closeHandler", TypeHelper.class2asm(SimpleConnectionPool.class));

				Proxy.proxyClass(data, new Class<?>[]{Connection.class}, (m, c) -> {
					if (m.getName().equals("close")) {
						c.visitSize(2, 1);
						c.clear();
						c.one(Opcodes.ALOAD_0);
						c.field(Opcodes.GETFIELD, data, closeHandler);
						c.one(Opcodes.ALOAD_0);
						c.invokeV("roj/sql/SimpleConnectionPool", "_reserve", "(Ljava/sql/Connection;)V");
						return true;
					} else if (m.getName().equals("isClosed")) {
						c.visitSizeMax(2, 0);

						c.invokeItf("java/sql/Connection", c.mn.name(), c.mn.rawDesc());

						Label label = new Label();
						c.jump(Opcodes.IFEQ, label);
						c.one(Opcodes.ICONST_1);
						c.one(Opcodes.IRETURN);

						c.label(label);
						c.one(Opcodes.ALOAD_0);
						c.field(Opcodes.GETFIELD, data, closeHandler);
						c.invokeV("roj/sql/SimpleConnectionPool", "_isClosed", "()Z");
						return true;
					}
					return false;
				}, closeHandler);

				FastInit.prepare(data);
				proxy = Helpers.cast(FastInit.make(data));
			}
		}

		Connection wrapConn = proxy.apply(new Object[] {connection, this});
		if (ld != null) ld.track(wrapConn);
		return wrapConn;
	}

	final boolean _isClosed() { return connId.get() == null; }
	final void _reserve(Connection conn) {
		if (ld != null) ld.remove(conn);

		Object[] id = connId.get();
		if (id == null) return;
		connId.remove();

		boolean closed;
		try {
			closed = ((Connection) id[1]).isClosed();
		} catch (Exception e) {
			closed = true;
		}

		if (closed) {
			pool[(int) id[0]] = null;
		} else {
			if (transfer.tryTransfer(id)) {
				multiplier = ThreadLocalRandom.current().nextInt();
				return;
			}
		}

		lock.unlock((int) id[0]);
	}
}
