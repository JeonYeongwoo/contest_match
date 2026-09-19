#!/bin/sh
set -e

# Start the server in the background so we can pull models into it, then bring it to the
# foreground so the container keeps running (and Nosana's health checks see it listening).
ollama serve &
SERVER_PID=$!

echo "Waiting for Ollama to accept connections..."
until ollama list >/dev/null 2>&1; do
  sleep 1
done

echo "Pulling chat model: ${CHAT_MODEL}"
ollama pull "${CHAT_MODEL}"

echo "Pulling embedding model: ${EMBEDDING_MODEL}"
ollama pull "${EMBEDDING_MODEL}"

echo "Ready. Chat + embeddings available at http://0.0.0.0:11434/v1/*"
wait "${SERVER_PID}"
