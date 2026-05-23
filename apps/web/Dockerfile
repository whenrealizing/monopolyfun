FROM node:22-alpine
WORKDIR /app

ENV NODE_ENV=production
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ARG API_BASE_URL=http://api:8080

ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV API_BASE_URL=${API_BASE_URL}

COPY apps/web/.next/standalone ./
COPY apps/web/.next/static ./apps/web/.next/static

EXPOSE 3000

CMD ["node", "apps/web/server.js"]
