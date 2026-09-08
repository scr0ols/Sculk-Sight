package com.scr0ols.sculksight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * ARCHITECTURE.md section 6.2's worker executor: a dedicated single thread, not the render
 * thread, that never runs two solves at once. RESEARCH-LOG.md R18 and DECISIONS.md ADR-046.
 *
 * <p>Every test constructs its own instance and closes it, rather than sharing one across the
 * class, because {@link ShellWorkerExecutor#close()} is a one-way operation (RESEARCH-LOG.md R18
 * point 2: {@code TracingExecutor.shutdownAndAwait} never restarts a stopped service, and neither
 * does this class) - a shared instance would make one test's shutdown corrupt every test after it.
 */
class ShellWorkerExecutorTest {

	private static final long AWAIT_SECONDS = 5L;

	@Test
	void aSubmittedTaskRunsOffTheCallingThread() throws InterruptedException {
		ShellWorkerExecutor executor = new ShellWorkerExecutor();
		Thread callingThread = Thread.currentThread();
		CountDownLatch done = new CountDownLatch(1);
		List<Thread> ran = new CopyOnWriteArrayList<>();

		try {
			executor.execute(() -> {
				ran.add(Thread.currentThread());
				done.countDown();
			});

			assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS), "task never ran");
			assertEquals(1, ran.size());
			assertNotEquals(callingThread, ran.get(0));
		} finally {
			executor.close();
		}
	}

	@Test
	void twoSubmittedTasksNeverOverlap() throws InterruptedException {
		// The hard requirement ARCHITECTURE.md section 6.2 names: solves for one sensor must not
		// run concurrently with each other. A dedicated single thread makes this true of every
		// pair of tasks, not only same-sensor ones, which is a strict superset of what is required.
		ShellWorkerExecutor executor = new ShellWorkerExecutor();
		AtomicInteger concurrent = new AtomicInteger();
		AtomicInteger maxConcurrentSeen = new AtomicInteger();
		CountDownLatch done = new CountDownLatch(2);

		Runnable task = () -> {
			int nowRunning = concurrent.incrementAndGet();
			maxConcurrentSeen.updateAndGet(previous -> Math.max(previous, nowRunning));

			// A short busy wait rather than Thread.sleep: long enough that two threads racing
			// into this block would overlap and be caught, short enough not to slow the suite.
			long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20);
			while (System.nanoTime() < until) {
				Thread.onSpinWait();
			}

			concurrent.decrementAndGet();
			done.countDown();
		};

		try {
			executor.execute(task);
			executor.execute(task);

			assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS), "tasks never finished");
			assertEquals(1, maxConcurrentSeen.get());
		} finally {
			executor.close();
		}
	}

	@Test
	void closeWaitsForAPendingTaskToFinishBeforeReturning() {
		ShellWorkerExecutor executor = new ShellWorkerExecutor();
		AtomicInteger finished = new AtomicInteger();

		executor.execute(() -> {
			long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50);
			while (System.nanoTime() < until) {
				Thread.onSpinWait();
			}

			finished.set(1);
		});

		executor.close();

		// RESEARCH-LOG.md R18: Util.shutdownExecutors() calls shutdownAndAwait(3, SECONDS), an
		// orderly shutdown() then awaitTermination(), not shutdownNow() first - so a task already
		// running is given the chance to finish rather than being interrupted immediately.
		assertEquals(1, finished.get());
	}

	@Test
	void closeIsIdempotent() {
		ShellWorkerExecutor executor = new ShellWorkerExecutor();

		executor.close();
		executor.close();
	}

	@Test
	void aTaskSubmittedAfterCloseIsDeclinedRatherThanThrown() {
		// Mirrors ShellUploadSlot.offer's own discipline: a slot or an executor that outlives its
		// one caller and is asked for one more thing after closing refuses quietly rather than
		// crashing the render thread that is in the middle of tearing everything else down.
		ShellWorkerExecutor executor = new ShellWorkerExecutor();
		executor.close();

		AtomicInteger ran = new AtomicInteger();
		executor.execute(ran::incrementAndGet);

		assertEquals(0, ran.get());
	}
}
