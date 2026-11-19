"""RabbitMQ consumer. Topology and message rules: api/asyncapi.yaml."""

import asyncio
import logging

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractRobustConnection

from app.db import SessionLocal
from app.events import DeviceRegistered, EntryMarked, PlanChanged, PlanDeleted, UserDeleted
from app.handlers.deletions import handle_plan_deleted, handle_user_deleted
from app.handlers.device_registered import handle_device_registered
from app.handlers.entry_marked import handle_entry_marked
from app.handlers.plan_changed import handle_plan_changed

log = logging.getLogger(__name__)

EXCHANGE = "otmetka.events"
QUEUE = "notifier.events"
DEAD_LETTER_EXCHANGE = "otmetka.events.dlx"
DEAD_LETTER_QUEUE = "notifier.events.dlq"

# Routing key -> (event model, handler). To consume another event, add a line.
HANDLERS = {
    "plan.changed": (PlanChanged, handle_plan_changed),
    "entry.marked": (EntryMarked, handle_entry_marked),
    "device.registered": (DeviceRegistered, handle_device_registered),
    "plan.deleted": (PlanDeleted, handle_plan_deleted),
    "user.deleted": (UserDeleted, handle_user_deleted),
}


def process(routing_key: str, body: bytes) -> None:
    """Parse and apply one message in one database transaction."""
    model, handler = HANDLERS[routing_key]
    event = model.model_validate_json(body)
    with SessionLocal.begin() as session:
        handler(session, event)


async def on_message(message: AbstractIncomingMessage) -> None:
    # Acknowledges on success. On any error the message is rejected without
    # requeue and RabbitMQ moves it to the dead-letter queue.
    async with message.process(requeue=False):
        try:
            # The handlers use blocking database calls, so they run in a thread.
            await asyncio.to_thread(process, message.routing_key, message.body)
        except Exception:
            log.exception("Message %s (%s) failed, dead-lettering", message.message_id, message.routing_key)
            raise


async def start_consumer(url: str) -> AbstractRobustConnection:
    """Declare the queues and start consuming. The connection reconnects by itself."""
    connection = await aio_pika.connect_robust(url)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=10)

    exchange = await channel.declare_exchange(EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
    dead_letters = await channel.declare_exchange(
        DEAD_LETTER_EXCHANGE, aio_pika.ExchangeType.FANOUT, durable=True
    )
    dead_letter_queue = await channel.declare_queue(DEAD_LETTER_QUEUE, durable=True)
    await dead_letter_queue.bind(dead_letters)

    queue = await channel.declare_queue(
        QUEUE, durable=True, arguments={"x-dead-letter-exchange": DEAD_LETTER_EXCHANGE}
    )
    for routing_key in HANDLERS:
        await queue.bind(exchange, routing_key)
    await queue.consume(on_message)
    log.info("Consuming %s from %s", ", ".join(HANDLERS), QUEUE)
    return connection
