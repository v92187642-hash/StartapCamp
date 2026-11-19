package ru.otmetka.core.events;

import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.otmetka.core.auth.UserDeleted;
import ru.otmetka.core.devices.DeviceRegistered;
import ru.otmetka.core.diary.EntryMarked;
import ru.otmetka.core.events.Messages.DeviceRegisteredData;
import ru.otmetka.core.events.Messages.EntryMarkedData;
import ru.otmetka.core.events.Messages.PlanDeletedData;
import ru.otmetka.core.events.Messages.UserDeletedData;
import ru.otmetka.core.plans.PlanChanged;
import ru.otmetka.core.plans.PlanDeleted;

/**
 * Turns what happens inside core into messages for RabbitMQ.
 *
 * A module raises a plain event object with Spring's ApplicationEventPublisher
 * inside its transaction. The listeners here run in that same transaction and
 * put the message into the outbox table; OutboxPublisher sends it once the
 * transaction has committed. A rolled-back change is never announced, and a
 * committed one is never forgotten. No module depends on this package; it
 * depends on their public types.
 *
 * To add a message: describe it in api/asyncapi.yaml, add its data record to
 * Messages and one listener method below.
 */
@Component
class RabbitEventRelay {

    private final Outbox outbox;

    RabbitEventRelay(Outbox outbox) {
        this.outbox = outbox;
    }

    @EventListener
    void on(EntryMarked event) {
        outbox.add("entry.marked", new EntryMarkedData(event.entryId(), event.planId(), event.status().value(),
                event.markedAt()));
    }

    /** Only the plan id is stored; the snapshot is built when the message is sent. */
    @EventListener
    void on(PlanChanged event) {
        outbox.add("plan.changed", Map.of("planId", event.planId()));
    }

    @EventListener
    void on(PlanDeleted event) {
        outbox.add("plan.deleted", new PlanDeletedData(event.planId()));
    }

    @EventListener
    void on(UserDeleted event) {
        outbox.add("user.deleted", new UserDeletedData(event.userId()));
    }

    @EventListener
    void on(DeviceRegistered event) {
        outbox.add("device.registered", new DeviceRegisteredData(event.userId(), event.provider(), event.token()));
    }
}
