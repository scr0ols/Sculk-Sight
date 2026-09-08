package com.scr0ols.sculksight.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.scr0ols.sculksight.SculkSight;

/**
 * ARCHITECTURE.md section 6.2's worker executor, named and shut down the way RESEARCH-LOG.md
 * R18 found the client's own background executors are: a dedicated thread, not the render
 * thread, that solves never run on concurrently with each other.
 *
 * <p><b>A dedicated thread rather than {@code Util.backgroundExecutor()}</b>, and this is a
 * requirement rather than a style choice. R18 point 3 traced {@code LevelRenderer}'s own
 * {@code SectionRenderDispatcher} - the closest vanilla analogue, a worker that compiles chunk
 * meshes and hands them to the render thread exactly as section 6.3's {@code ShellUploadSlot}
 * does here - and found it submits to {@code Util.backgroundExecutor()} directly, a shared
 * {@code ForkJoinPool} sized by {@code Util.maxAllowedExecutorThreads()}. That pool does not
 * serialise what is submitted to it, and section 6.2's second hard requirement - solves for one
 * sensor must not run concurrently with each other - is exactly what a shared, multi-threaded
 * pool would not give this mod for free. A single thread owned by this class gives it for every
 * pair of tasks, not only same-sensor ones, which satisfies the requirement without depending on
 * anything about how many sensors v0.0 ever has active at once.
 *
 * <p><b>Because the thread is private to this instance rather than shared, this class must shut
 * it down itself.</b> Vanilla's own shared pool is shut down exactly once, for the whole game, by
 * {@code Util.shutdownExecutors()} - {@code SectionRenderDispatcher.dispose()} (called from
 * {@code LevelRenderer.close()}) tears down only its own queue and buffers, never the executor it
 * borrowed. {@link #close()} mirrors {@code Util.shutdownExecutors()}'s own shutdown of each
 * {@code TracingExecutor} it owns, read from {@code TracingExecutor.shutdownAndAwait(long,
 * TimeUnit)}'s bytecode: {@code shutdown()}, then {@code awaitTermination}, and only if that
 * times out or is interrupted, {@code shutdownNow()}. The three-second timeout is copied from the
 * same call ({@code Util.shutdownExecutors()} passes {@code 3L, TimeUnit.SECONDS} for both the
 * background pool and the IO pool) rather than chosen independently.
 *
 * <p><b>Where the shutdown call belongs.</b> {@code Minecraft.close()} calls
 * {@code Util.shutdownExecutors()} on the client thread, late in an orderly teardown - after
 * {@code GameNarrator.destroy()} and {@code FreeTypeUtil.destroy()}, before the GPU surface closes
 * and {@code RenderSystem.shutdownRenderer()} runs - itself reached from
 * {@code Minecraft.exitWorldAndClose()}, which {@code Main.main} calls immediately after
 * {@code minecraft.run()} returns. {@link ShellRenderer#onClientStopping()} is this mod's own
 * analogue of that moment, already called from each loader's own client-stopping event on the
 * client thread, and is where {@link #close()} belongs.
 *
 * <p><b>Submitted to since DECISIONS.md ADR-048's wiring.</b> {@link ShellRenderer#solveAndEncode}
 * runs on this executor's one thread; {@link ShellRenderer#runSolve} itself stays on the client
 * thread, for the snapshot phase section 6.2 names ahead of the solve (DECISIONS.md ADR-047).
 */
final class ShellWorkerExecutor implements AutoCloseable {

	private static final long SHUTDOWN_TIMEOUT_SECONDS = 3L;

	private final ExecutorService service = Executors.newSingleThreadExecutor(new NamedThreadFactory());

	/**
	 * Runs {@code task} on this executor's one worker thread, never concurrently with any other
	 * task submitted here. Declines silently rather than throwing once {@link #close()} has run -
	 * the same discipline {@link ShellUploadSlot#offer} applies to an offer after its slot closes,
	 * with no log line either, matching that method exactly - since the only caller of a closed
	 * executor is a solve racing the game's own shutdown, which is expected there and not an error.
	 */
	void execute(Runnable task) {
		try {
			service.execute(task);
		} catch (RejectedExecutionException e) {
			// Intentionally not logged - see the javadoc above.
		}
	}

	/**
	 * Render thread. Shuts the worker thread down, waiting up to three seconds for whatever it is
	 * doing to finish before forcing it, mirroring {@code Util.shutdownExecutors()} exactly
	 * (see the class javadoc for the read behind both the shape and the timeout).
	 */
	@Override
	public void close() {
		service.shutdown();

		boolean terminated;

		try {
			terminated = service.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// Util.shutdownExecutors()'s own TracingExecutor.shutdownAndAwait does not restore the
			// interrupt flag here either (RESEARCH-LOG.md R18 point 2's bytecode read) - it falls
			// straight through to the forced shutdown below, which is what is mirrored here.
			terminated = false;
		}

		if (!terminated) {
			service.shutdownNow();
		}
	}

	/**
	 * Names the one thread this executor ever creates and logs what it would otherwise lose: an
	 * exception thrown out of a task rather than returned from a {@code Future} nobody reads.
	 * {@code Util.makeExecutor} pairs the same two things - a named thread and an uncaught-exception
	 * handler - though its own handler feeds vanilla's crash-report machinery (R18 point 2), which
	 * this mod does not use anywhere else; logging through {@link SculkSight#LOGGER} matches how
	 * every other caught failure in this package is already reported.
	 */
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
