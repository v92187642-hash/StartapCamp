FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:0.12.21 /uv /bin/uv
WORKDIR /app
# Installs dependencies in their own layer, so a code change does not repeat it.
COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --frozen --no-dev
COPY alembic.ini ./
COPY alembic alembic
COPY app app
ENV PATH="/app/.venv/bin:$PATH"
EXPOSE 8000
# Migrations run on every start; Alembic does nothing when the schema is current.
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8000"]
