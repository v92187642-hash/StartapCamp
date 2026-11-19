FROM postgres:16-alpine
# Init scripts are copied into the image so compose does not bind a host path
# into the container (Dokploy / a VPS must not mount arbitrary filesystem dirs).
COPY init-roles.sh /docker-entrypoint-initdb.d/init-roles.sh
