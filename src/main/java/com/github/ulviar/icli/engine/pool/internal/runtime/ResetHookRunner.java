package com.github.ulviar.icli.engine.pool.internal.runtime;

import com.github.ulviar.icli.engine.pool.api.LeaseScope;
import com.github.ulviar.icli.engine.pool.api.PoolDiagnosticsListener;
import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetHook;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetOutcome;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest;
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker;
import java.util.List;

/**
 * Executes the configured {@link ResetHook reset hooks} after a request finishes and determines whether the associated
 * worker should be retired. Hooks are evaluated sequentially using the {@link PoolWorker} and the lease scope that was
 * active during the request. When a hook signals {@link ResetOutcome#RETIRE}, the runner reports that the worker must
 * be retired and records {@link WorkerRetirementReason#RESET_HOOK_REQUESTED}. Any exception thrown by a hook is treated
 * as a hard failure: the pool reports the error via {@link PoolDiagnosticsListener#workerFailed(int, Throwable)} and
 * retires the worker with {@link WorkerRetirementReason#RESET_HOOK_FAILURE}.
 *
 * <p>The runner respects workers that have already been marked for retirement (for example by the pool state when a
 * reuse limit is reached) and never overrides their existing {@link WorkerRetirementReason}.
 */
public final class ResetHookRunner {

    private final List<ResetHook> hooks;
    private final PoolDiagnosticsListener diagnostics;

    /**
     * Creates a runner.
     *
     * @param hooks       reset hooks to evaluate after each completed lease
     * @param diagnostics sink used to surface hook failures
     */
    public ResetHookRunner(List<ResetHook> hooks, PoolDiagnosticsListener diagnostics) {
        this.hooks = List.copyOf(hooks);
        this.diagnostics = diagnostics;
    }

    /**
     * Executes configured {@link ResetHook} instances synchronously in declaration order and returns a
     * {@link RetireDecision} describing whether the worker must be retired.
     *
     * <p><strong>Short‑circuiting:</strong> as soon as a hook returns
     * {@link ResetOutcome#RETIRE}, no further hooks are invoked and the result is
     * {@code RetireDecision.retire(RESET_HOOK_REQUESTED)}.
     *
     * <p><strong>Existing retirement:</strong> if {@code worker} is already marked for retirement when this method is
     * called, hooks are not executed and the method returns
     * {@code RetireDecision.retire(worker.retirementCause())}.
     *
     * <p><strong>Hook failures:</strong> if a hook throws, the failure is reported via
     * {@link com.github.ulviar.icli.engine.pool.api.PoolDiagnosticsListener#workerFailed(int, Throwable)}
     * with the worker id, and the method returns {@code RetireDecision.retire(RESET_HOOK_FAILURE)}.
     *
     * <p><strong>No retirement:</strong> if all hooks complete normally and return {@link ResetOutcome#CONTINUE},
     * the method returns {@link RetireDecision#keep()}.
     *
     * <p>Hooks are invoked on the caller thread; implementations should avoid long‑running or blocking work to keep the
     * pool’s release path responsive.
     *
     * @param worker  worker whose session is being reset
     * @param scope   immutable snapshot of the lease scope used during the request (see {@link
     *                com.github.ulviar.icli.engine.pool.api.LeaseScope})
     * @param request context describing why the reset is being performed
     *
     * @return {@link RetireDecision#retire(com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason)} when the
     * worker is already marked for retirement, when a hook requests retirement, or when a hook fails; otherwise
     * {@link RetireDecision#keep()}
     *
     * @implSpec This method does not mutate {@code worker} and never overwrites an existing non‑{@code NOT_RETIRED}
     * retirement cause; it only computes a decision for the caller to apply.
     *
     * @see ResetHook
     * @see ResetOutcome
     * @see RetireDecision
     * @see com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason
     */
    public RetireDecision run(PoolWorker worker, LeaseScope scope, ResetRequest request) {
        boolean retire = worker.retireRequested();
        WorkerRetirementReason retireReason = worker.retirementCause();
        boolean allowHooks = !retire || request.reason() == ResetRequest.Reason.TIMEOUT;
        if (!allowHooks) {
            return RetireDecision.retire(retireReason);
        }
        for (ResetHook hook : hooks) {
            try {
                ResetOutcome outcome = hook.reset(worker.session(), scope, request);
                if (outcome == ResetOutcome.RETIRE) {
                    retire = true;
                    retireReason = WorkerRetirementReason.RESET_HOOK_REQUESTED;
                    break;
                }
            } catch (Exception ex) {
                diagnostics.workerFailed(worker.id(), ex);
                return RetireDecision.retire(WorkerRetirementReason.RESET_HOOK_FAILURE);
            }
        }
        if (retire) {
            return RetireDecision.retire(retireReason);
        }
        return RetireDecision.keep();
    }
}
