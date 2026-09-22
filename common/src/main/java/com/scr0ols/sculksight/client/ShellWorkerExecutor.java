package com.scr0ols.sculksight.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.scr0ols.sculksight.SculkSight;

final class ShellWorkerExecutor implements AutoCloseable {

	private static final long SHUTDOWN_TIMEOUT_SECONDS = 3L;

	private final ExecutorService service = Executors.newSingleThreadExecutor(new NamedThreadFactory());

	void execute(Runnable task) {
		try {
			service.execute(task);
		} catch (RejectedExecutionException e) {
		}
	}

	/** Render thread. Shuts the worker thread down, forcing it after a short wait. */
	@Override
	public void close() {
		service.shutdown();

		boolean terminated;

		try {
			terminated = service.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			terminated = false;
		}

		if (!terminated) {
			service.shutdownNow();
		}
	}

	private static final class NamedThreadFactory implements ThreadFactory {

		private static final AtomicInteger COUNT = new AtomicInteger(1);

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r, "Sculk Sight worker-" + COUNT.getAndIncrement());
			thread.setDaemon(true);
			thread.setUncaughtExceptionHandler((t, e) ->
					SculkSight.LOGGER.error("[sculksight] uncaught exception on {}", t.getName(), e));

			return thread;
		}
	}
}
