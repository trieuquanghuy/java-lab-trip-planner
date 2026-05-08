# Multi-stage frontend container — built INSIDE the container so `docker compose up --wait`
# works on a fresh checkout with no prior `pnpm install` or `pnpm build` (ROADMAP SC#1).
#
# Build context: ./frontend (set in infra/docker-compose.yml's frontend service via
# `build.context: ../frontend`). VITE_API_URL is passed as a build-arg because Vite
# substitutes `import.meta.env.VITE_API_URL` at build time, so it MUST be baked in.

# Stage 1: build the production bundle.
FROM node:20-alpine AS builder
WORKDIR /app

# Activate pnpm via Corepack (pinned to the same version package.json declares).
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate

# Build-time arg for the API gateway base URL — defaulted to the compose host:port
# but overridable per environment. Vite inlines this into the JS bundle.
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}

# Copy lockfile + manifest first so dep install layer is cached when only src/ changes.
COPY package.json pnpm-lock.yaml .npmrc ./
RUN pnpm install --frozen-lockfile

# Now copy the rest of the project (src/, public/, configs).
COPY . ./
RUN pnpm build

# Verify the build emitted dist/index.html — fail the image build if not.
RUN test -f /app/dist/index.html

# Stage 2: serve the built static bundle via nginx.
FROM nginx:alpine AS runtime

# Copy the built dist into nginx's default web root.
COPY --from=builder /app/dist /usr/share/nginx/html

# SPA fallback: rewrite all unknown routes to /index.html so React Router (Phase 7+)
# handles them. Listen on 5173 to match the compose port mapping.
RUN printf 'server {\n  listen 5173;\n  server_name _;\n  root /usr/share/nginx/html;\n  index index.html;\n  location / {\n    try_files $uri $uri/ /index.html;\n  }\n}\n' > /etc/nginx/conf.d/default.conf

# Remove default 80 server block to avoid port conflict (nginx:alpine ships one).
RUN rm -f /etc/nginx/conf.d/default.conf.backup 2>/dev/null || true

EXPOSE 5173

# Healthcheck verifies the served bundle still contains the locked <title>.
# Catches the case where the build silently regressed the index.html template.
HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=10s \
  CMD wget -qO- http://localhost:5173/ | grep -q '<title>Trip Planner</title>' || exit 1

CMD ["nginx", "-g", "daemon off;"]
