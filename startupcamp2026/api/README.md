# api

Two contracts, both the source of truth: change them first, then the code.

- `openapi.yaml`: the public API served by `core`. Web and Android API clients
  are generated from it.
- `asyncapi.yaml`: the events `core` publishes to RabbitMQ and `notifier`
  consumes.

Both describe everything that is implemented; `docs/architecture.md` has a
one-page summary of each.

## Changing a contract

1. Edit the YAML file. Every change to `openapi.yaml` is reviewed by one Java
   and one Python developer.
2. Change the code that serves or publishes it.
3. The clients pick the change up on their next build: `apps/web` regenerates
   its types on `npm run dev`, `npm test` and `npm run build`; `apps/android`
   regenerates on every Gradle build.

`core` is written by hand against `openapi.yaml`, and `notifier` against
`asyncapi.yaml`; nothing checks them against the contracts automatically yet,
so keep the contract open while writing an endpoint or a handler.

## Reading them

Paste a file into https://editor.swagger.io (OpenAPI) or
https://studio.asyncapi.com (AsyncAPI) for a rendered view.
