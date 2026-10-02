package edu.cit.verano.channel;

/**
 * PACKAGE-PRIVATE context holder for thread-local flags during Tiangge order processing.
 * Prevents premature stock sync domain events before Tiangge receives the order decision.
 */
final class TianggeContext {

    private TianggeContext() {}

    static final ThreadLocal<Boolean> IN_TIANGGE_PROCESSING = ThreadLocal.withInitial(() -> false);
}

