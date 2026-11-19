# Build context is the repository root: the API client is generated from api/openapi.yaml.
FROM node:24-alpine AS build
WORKDIR /repo/apps/web
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY api /repo/api
COPY apps/web ./
RUN npm run build

FROM nginx:1.29-alpine
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/web/dist /usr/share/nginx/html
EXPOSE 80
