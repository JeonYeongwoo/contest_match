package com.contestmate.ai;

import java.util.Optional;

/**
 * Abstraction over an OpenAI-compatible inference server (OpenAI itself, or a self-hosted
 * vLLM/Ollama instance such as the one deployed on Nosana GPU nodes). Never receives the
 * database connection string or any user PII beyond the text being processed.
 */
public interface LlmClient {

    boolean isConfigured();

    /** Plain chat completion; returns empty when the LLM is unreachable or misconfigured. */
    Optional<String> chatComplete(String systemPrompt, String userPrompt);

    /** Returns empty when embeddings are unavailable; callers must have a non-vector fallback. */
    Optional<float[]> embed(String text);
}
