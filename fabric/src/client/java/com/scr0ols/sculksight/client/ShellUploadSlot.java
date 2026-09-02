package com.scr0ols.sculksight.client;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.blaze3d.vertex.MeshData;

import org.jspecify.annotations.Nullable;

/**
 * One pending mesh per shell, handed from a worker thread to the render thread.
 * ARCHITECTURE.md section 6.3, recorded as c-docs/DECISIONS.md ADR-017.
 *
 * <p><b>This exists because vanilla has no such queue.</b> R13 point 14 established that
 * {@code RenderSystem.queueFencedTask} is not a worker-to-render hand-off - enqueuing is itself a
 * GPU call, so a caller must already be on the render thread, and its backing deque carries no
 * synchronisation. The mod supplies the hand-off itself, which is why this is a named component of
 * the architecture rather than a platform service.
 *
 * <p><b>The ownership rule, complete: whoever removes a reference from the slot closes it.</b>
 * A {@link MeshData} holds native memory the garbage collector will not reclaim (R13 points 7 and
 * 10), so exactly one thread must close each one. The worker that displaces an older pending
 * result closes what it displaced; the worker whose result is rejected as stale closes its own;
 * the render thread closes what {@link #take()} gave it, after the bytes have been copied; and
 * {@link #close()} closes whatever is left.
 *
 * <p><b>Why that is free of double-closes without a lock, mechanically.</b>
 * {@link AtomicReference#getAndSet} and a successful {@code compareAndSet} each hand a given
 * reference to exactly one caller, so two threads cannot both receive the same pending result and
 * therefore cannot both decide to close the same mesh. A plain field with a null check would not
 * have this property: two workers finishing at once could read the same old reference and both
 * close it, which is a native-memory double-free.
 *
 * <p><b>Why the revision is needed.</b> Without it, a solve started before an invalidation and
 * finishing after a newer one would displace the fresher mesh and the mod would draw a stale
 * shell. Drawing the wrong shape is what PLAN.md section 1 ranks as worse than drawing nothing.
 */
public final class ShellUploadSlot implements AutoCloseable {

	private record Pending(long revision, MeshData mesh) {
	}

	private final AtomicReference<@Nullable Pending> pending = new AtomicReference<>();

	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Worker thread. Offers a finished mesh stamped with the revision it was solved for.
	 *
	 * @return false if a newer result was already pending or the slot is closed, in which case the
	 *         offered mesh has been closed here and the caller must not touch it again
	 */
	public boolean offer(long revision, MeshData mesh) {
		while (true) {
			if (closed.get()) {
				mesh.close();
				return false;
			}

			Pending current = pending.get();

			if (current != null && current.revision() >= revision) {
				mesh.close();
				return false;
			}

			if (pending.compareAndSet(current, new Pending(revision, mesh))) {
				if (current != null) {
					current.mesh().close();
				}

				// The close-after-close race: close() sets the flag and then drains, so an offer
				// that stored after that drain re-checks the flag here and drains again. Both
				// drains use getAndSet, so whichever runs second gets null and does nothing, and
				// whichever gets the reference closes it. A worker finishing after the world
				// unloaded therefore frees its own memory rather than leaking it.
				if (closed.get()) {
					drain();
					return false;
				}

				return true;
			}
		}
	}

	/** Render thread. Returns the pending mesh and clears the slot; the caller then owns it. */
	public @Nullable MeshData take() {
		Pending taken = pending.getAndSet(null);
		return taken == null ? null : taken.mesh();
	}

	/** Render thread. Closes anything still pending; subsequent offers close their argument. */
	@Override
	public void close() {
		closed.set(true);
		drain();
	}

	private void drain() {
		Pending taken = pending.getAndSet(null);

		if (taken != null) {
			taken.mesh().close();
		}
	}
}
