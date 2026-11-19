package ru.otmetka.core.events;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Asks for the outbox to be sent "now, but not on my thread". The request
 * that raised an event answers the user at once; sending happens on one
 * background thread, so a broker that is slow or down never slows a request.
 */
@Component
class OutboxTrigger {

    private static final Logger log = LoggerFactory.getLogger(OutboxTrigger.class);

    private final OutboxPublisher publisher;
    private final Executor executor;
    /** True while a run is waiting to start: a hundred requests ask for one run, not a hundred. */
    private final AtomicBoolean queued = new AtomicBoolean();

    OutboxTrigger(OutboxPublisher publisher, @Value("${otmetka.outbox.inline:false}") boolean inline) {
        this.publisher = publisher;
        // Inline is for tests only: they check what was sent right after a request returns.
        this.executor = inline ? Runnable::run : Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "outbox");
            thread.setDaemon(true);
            return thread;
        });
    }

    void publishSoon() {
        if (!queued.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            // Cleared before the run starts: a row added during the run asks for another one.
            queued.set(false);
            try {
                publisher.publishPending();
            } catch (RuntimeException e) {
                // Nothing is lost: the rows are still in the table and the timer will send them.
                log.warn("Sending the outbox failed; the timer will retry: {}", e.toString());
            }
        });
    }
}
