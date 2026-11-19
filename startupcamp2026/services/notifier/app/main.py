import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.alerts import poll_forever
from app.config import settings
from app.consumer import start_consumer
from app.push.registry import build_providers
from app.reports import router as reports_router

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def lifespan(app: FastAPI):
    connection = await start_consumer(settings.rabbitmq_url) if settings.consumer_enabled else None
    poller = asyncio.create_task(poll_forever(build_providers(settings))) if settings.alerts_enabled else None
    yield
    if poller:
        poller.cancel()
    if connection:
        await connection.close()


app = FastAPI(title="Отметка notifier", lifespan=lifespan)
app.include_router(reports_router)


@app.exception_handler(RequestValidationError)
async def log_invalid_request(request: Request, error: RequestValidationError) -> JSONResponse:
    # Where the body was wrong, never the body itself: a report carries the user's own text.
    problems = [(".".join(map(str, e["loc"])), e["type"]) for e in error.errors()]
    logging.getLogger(__name__).warning("Invalid request to %s: %s", request.url.path, problems)
    return JSONResponse(status_code=422, content={"detail": [{"loc": loc, "type": kind} for loc, kind in problems]})


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
