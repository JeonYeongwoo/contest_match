package com.contestmate.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Talks to the `contest_embeddings` table directly via JDBC/pgvector SQL operators.
 * Kept out of JPA/Hibernate entirely because Hibernate 5 (Spring Boot 2.7) has no
 * built-in understanding of the Postgres `vector` column type.
 */
@Repository
public class VectorSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(long contestId, float[] embedding) {
        String vectorLiteral = toVectorLiteral(embedding);
        jdbcTemplate.update(
                "INSERT INTO contest_embeddings (contest_id, embedding) VALUES (?, ?::vector) " +
                        "ON CONFLICT (contest_id) DO UPDATE SET embedding = EXCLUDED.embedding",
                contestId, vectorLiteral);
    }

    /** Returns the most similar existing contests, ordered by cosine similarity descending. */
    public List<SimilarContest> findMostSimilar(float[] embedding, int limit) {
        String vectorLiteral = toVectorLiteral(embedding);
        return jdbcTemplate.query(
                "SELECT contest_id, 1 - (embedding <=> ?::vector) AS similarity " +
                        "FROM contest_embeddings ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> new SimilarContest(rs.getLong("contest_id"), rs.getDouble("similarity")),
                vectorLiteral, vectorLiteral, limit);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    public static class SimilarContest {
        public final long contestId;
        public final double similarity;

        public SimilarContest(long contestId, double similarity) {
            this.contestId = contestId;
            this.similarity = similarity;
        }
    }
}
