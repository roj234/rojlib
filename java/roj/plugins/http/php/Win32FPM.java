package roj.plugins.http.php;

import roj.collect.SimpleList;
import roj.concurrent.timing.Scheduler;
import roj.io.FastFailException;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.Request;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj234
 * @since 2024/7/2 1:55
 */
public class Win32FPM extends fcgiManager {
	final String[] command;
	final int minProcess, maxProcess, timeout;
	final ConcurrentHashMap<Process, fcgiConnection> processes = new ConcurrentHashMap<>();

	File docRoot;

	public Win32FPM(int minProcess, int maxProcess, int timeout, String... command) {
		this.command = command;
		this.minProcess = minProcess;
		this.maxProcess = maxProcess;
		this.timeout = timeout;

		if (timeout != 0) Scheduler.getDefaultScheduler().loop(this::killStale, timeout);
	}

	public boolean killStale() {
		if (processes.size() > minProcess) {
			long deadline = System.currentTimeMillis() - timeout;
			var itr = processes.entrySet().iterator();
			if (itr.hasNext()) {
				var next = itr.next();
				if (next.getValue().idleTime < deadline) {
					next.getKey().destroy();
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

	protected void fcgi_set_param(Request req, Map<String, String> param) throws IOException {
		String scriptName = req.path();

		File scriptFile = new File(docRoot, scriptName);
		if (!scriptFile.isFile() || !scriptFile.getName().endsWith(".php")) throw new IllegalRequestException(404);

		param.put("DOCUMENT_ROOT", docRoot.getAbsolutePath());
		param.put("SCRIPT_NAME", scriptName);
		param.put("PATH_TRANSLATED", scriptFile.getAbsolutePath());
		param.put("SCRIPT_FILENAME", scriptFile.getAbsolutePath());
	}

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition hasSpace = lock.newCondition();
	protected void fcgi_attach(fcgiResponse response, Map<String, String> param) throws IOException {
		while (true) {
			for (var itr = processes.entrySet().iterator(); itr.hasNext(); ) {
				var entry = itr.next();
				if (!entry.getKey().isAlive()) {
					itr.remove();
					continue;
				}

				var conn = entry.getValue();
				int code = conn.attach(response, param);
				if (code > 0) return;
				if (code < 0) {
					entry.getKey().destroy();
					itr.remove();
				}
			}

			if (processes.size() < maxProcess) {
				SimpleList<String> myArgs = new SimpleList<>(command.length+1);
				myArgs.addAll(command);

				int port = 10000 + (Math.abs((int) System.nanoTime()) % 40000);
				myArgs.add(String.valueOf(port));

				var key = new ProcessBuilder(myArgs).directory(new File(myArgs.get(0)).getParentFile()).start();
				var val = new fcgiConnection(this, port);

				if (val.attach(response, param) > 0) {
					processes.put(key, val);
					return;
				} else {
					key.destroy();
				}
			}

			lock.lock();
			try {
				if (!hasSpace.await(1000, TimeUnit.MILLISECONDS))
					throw new FastFailException("Win32FPM ERROR: No Process Available");
			} catch (InterruptedException e) {
				throw new ClosedByInterruptException();
			} finally {
				lock.unlock();
			}
		}
	}

	private void hasSpace() {
		lock.lock();
		hasSpace.signalAll();
		lock.unlock();
	}

	@Override
	protected void connectionClosed(fcgiConnection conn) {hasSpace();}
	@Override
	protected void requestFinished(fcgiConnection conn) {hasSpace();}
}