package roj.plugins.web.php;

import roj.collect.RingBuffer;
import roj.collect.ArrayList;
import roj.concurrent.Timer;
import roj.concurrent.Task;
import roj.http.server.IllegalRequestException;
import roj.http.server.Request;
import roj.util.FastFailException;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj234
 * @since 2024/7/2 1:55
 */
public class Win32FPM extends fcgiManager implements Task {
	final List<String> command;
	final int minProcess, maxProcess, timeout;
	final ConcurrentHashMap<Process, fcgiConnection> processes = new ConcurrentHashMap<>();
	int portBase = 40000;
	private int launchedSize;

	File docRoot;

	public Win32FPM(int minProcess, int maxProcess, int timeout, List<String> command) {
		this.command = command;
		this.minProcess = minProcess;
		this.maxProcess = maxProcess;
		this.timeout = timeout;

		if (timeout != 0) Timer.getDefault().loop(this::killStale, timeout);
	}

	public boolean killStale() {
		if (processes.size() > minProcess) {
			long deadline = System.currentTimeMillis() - timeout;
			var itr = processes.entrySet().iterator();
			if (itr.hasNext()) {
				var next = itr.next();
				if (next.getValue().idleTime < deadline) {
					next.getKey().destroy();
					itr.remove();
					return true;
				}
			}
		}
		return false;
	}

	public void killAll() {
		for (Process process : processes.keySet()) {
			process.destroy();
		}
	}

	private boolean stop;
	public void stop() {
		lock.lock();
		try {
			stop = true;
			stateChanged.signal();
		} finally {
			lock.unlock();
		}
	}

	protected void fcgi_set_param(Request req, Map<String, String> param) throws IllegalRequestException {
		String scriptName = req.path();

		File scriptFile = new File(docRoot, scriptName);
		if (!scriptFile.isFile() || !scriptFile.getName().endsWith(".php")) throw new IllegalRequestException(404);

		param.put("DOCUMENT_ROOT", docRoot.getAbsolutePath());
		param.put("SCRIPT_NAME", scriptName.replace('/', File.separatorChar));
		param.put("PATH_TRANSLATED", scriptFile.getAbsolutePath());
		param.put("SCRIPT_FILENAME", scriptFile.getAbsolutePath());
	}

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition stateChanged = lock.newCondition();

	final RingBuffer<fcgiContent> pendingTask = RingBuffer.lazy(1024);
	private final RingBuffer<Map<String, String>> pendingParam = RingBuffer.lazy(1024);

	protected void fcgi_attach(fcgiContent response, Map<String, String> param) {
		lock.lock();
		try {
			if (tryAttachGuarded(response, param)) return;

			if (!pendingTask.offerLast(response)) throw new FastFailException("排队的请求太多");
			pendingParam.offerLast(param);
			stateChanged.signal();
		} catch (Exception e) {
			response.fail(e);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void run() throws Exception {
		while (true) {
			fcgiContent response;
			Map<String, String> param;

			for(;;) {
				lock.lock();
				try {
					response = pendingTask.pollFirst();
					if (response != null) {
						param = pendingParam.removeFirst();

						var error = response.isInvalid();
						if (error == null) break;
						fcgiManager.LOGGER.warn("请求({})不再有效:{}",response,error);
					}

					if (stop) return;
					while (pendingTask.isEmpty()) stateChanged.await();
					if (stop) return;
				} finally {
					lock.unlock();
				}
			}

			for(;;) try {
				if (tryAttachGuarded(response, param)) break;

				if (processes.size() < maxProcess) {
					ArrayList<String> myArgs = new ArrayList<>(command.size()+1);
					myArgs.addAll(command);

					int port = portBase + launchedSize++ % 1000;
					myArgs.add(String.valueOf(port));

					var key = new ProcessBuilder(myArgs).directory(new File(myArgs.get(0)).getParentFile()).start();
					var val = new fcgiConnection(this, port);

					try {
						if (val.attach(response, param) > 0) {
							processes.put(key, val);
							break;
						}
					} catch (Exception e) {
						key.destroy();
						throw e;
					}

					key.destroy();
				}

				lock.lock();
				try {
					stateChanged.await();
				} finally {
					lock.unlock();
				}
			} catch (Exception e) {
				response.fail(e);
			}
		}
	}

	private boolean tryAttachGuarded(fcgiContent response, Map<String, String> param) throws Exception {
		for (var itr = processes.entrySet().iterator(); itr.hasNext(); ) {
			var entry = itr.next();
			if (!entry.getKey().isAlive()) {
				itr.remove();
				continue;
			}

			var conn = entry.getValue();
			int code = conn.attach(response, param);
			if (code > 0) return true;
			if (code < 0) {
				entry.getKey().destroy();
				itr.remove();
			}
		}
		return false;
	}

	private void hasSpace() {
		lock.lock();
		stateChanged.signalAll();
		lock.unlock();
	}

	@Override
	protected void connectionClosed(fcgiConnection conn) {hasSpace();}
	@Override
	protected void requestFinished(fcgiConnection conn) {hasSpace();}
}