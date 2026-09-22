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
		ShellWorkerExecutor executor = new ShellWorkerExecutor();
		AtomicInteger concurrent = new AtomicInteger();
		AtomicInteger maxConcurrentSeen = new AtomicInteger();
		CountDownLatch done = new CountDownLatch(2);

		Runnable task = () -> {
			int nowRunning = concurrent.incrementAndGet();
			maxConcurrentSeen.updateAndGet(previous -> Math.max(previous, nowRunning));

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
		ShellWorkerExecutor executor = new ShellWorkerExecutor();
		executor.close();

		AtomicInteger ran = new AtomicInteger();
		executor.execute(ran::incrementAndGet);

		assertEquals(0, ran.get());
	}
}
