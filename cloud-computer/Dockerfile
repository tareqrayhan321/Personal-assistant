FROM node:22-slim
# Tools the agent will most likely want; add your own.
RUN apt-get update && apt-get install -y --no-install-recommends git curl python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --create-home runner
WORKDIR /app
COPY server.js package.json ./
RUN mkdir /workspace && chown runner /workspace
USER runner
ENV WORKDIR=/workspace HOST=0.0.0.0 PORT=8787
EXPOSE 8787
CMD ["node", "server.js"]
