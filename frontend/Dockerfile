# syntax=docker/dockerfile:1
ARG SERVICEHUB_NODE_BUILD_IMAGE
ARG SERVICEHUB_NGINX_IMAGE

FROM ${SERVICEHUB_NODE_BUILD_IMAGE} AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
RUN npm run build

FROM ${SERVICEHUB_NGINX_IMAGE}
COPY frontend/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/frontend/dist/ /usr/share/nginx/html/
EXPOSE 8080
