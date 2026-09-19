package com.contestmate.ai;

import com.contestmate.contest.Contest;
import com.contestmate.contest.ContestRepository;
import com.contestmate.contest.ContestSource;
import com.contestmate.contest.ContestSourceRepository;
import com.contestmate.contest.ContestStatus;
import com.contestmate.contest.IndividualOrTeam;
import com.contestmate.contest.OnlineOffline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Decides whether a freshly extracted ContestDraft is the same real-world contest as one
 * already stored, using pgvector cosine similarity over title+description embeddings when an
 * embedding model is configured, or an exact-title fallback otherwise. Either way, the raw
 * source URL is always recorded via ContestSource -- merging never discards an origin link.
 */
@Service
public class DedupService {

    private final ContestRepository contestRepository;
    private final ContestSourceRepository contestSourceRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final LlmClient llmClient;
    private final double similarityThreshold;

    public DedupService(ContestRepository contestRepository,
                         ContestSourceRepository contestSourceRepository,
                         VectorSearchRepository vectorSearchRepository,
                         LlmClient llmClient,
                         @Value("${contest-mate.dedup.similarity-threshold:0.90}") double similarityThreshold) {
        this.contestRepository = contestRepository;
        this.contestSourceRepository = contestSourceRepository;
        this.vectorSearchRepository = vectorSearchRepository;
        this.llmClient = llmClient;
        this.similarityThreshold = similarityThreshold;
    }

    @Transactional
    public Contest mergeOrCreate(ContestDraft draft, Long sourceId, String sourceUrl) {
        Optional<float[]> embedding = llmClient.embed(safe(draft.title) + " " + safe(draft.description));

        Optional<Contest> existing = embedding.isPresent()
                ? findSimilarByEmbedding(embedding.get())
                : findSimilarByTitle(draft.title);

        Contest merged = existing.map(c -> updateIfMoreConfident(c, draft)).orElseGet(() -> createContest(draft));
        final Contest contest = contestRepository.save(merged);

        boolean alreadyLinked = contestSourceRepository.findByContestId(contest.getId()).stream()
                .anyMatch(cs -> cs.getSourceUrl().equals(sourceUrl));
        if (!alreadyLinked) {
            contestSourceRepository.save(new ContestSource(contest, sourceId, sourceUrl));
        }

        embedding.ifPresent(vec -> vectorSearchRepository.upsert(contest.getId(), vec));
        return contest;
    }

    private Optional<Contest> findSimilarByEmbedding(float[] embedding) {
        return vectorSearchRepository.findMostSimilar(embedding, 1).stream()
                .filter(candidate -> candidate.similarity >= similarityThreshold)
                .findFirst()
                .flatMap(candidate -> contestRepository.findById(candidate.contestId));
    }

    private Optional<Contest> findSimilarByTitle(String title) {
        if (title == null || title.isBlank()) return Optional.empty();
        return contestRepository.findAll().stream()
                .filter(c -> c.getTitle() != null && c.getTitle().trim().equalsIgnoreCase(title.trim()))
                .findFirst();
    }

    private Contest createContest(ContestDraft draft) {
        Contest contest = new Contest();
        applyDraft(contest, draft);
        return contest;
    }

    /** Only overwrites a field when the existing value is unconfirmed/missing and the new draft has one. */
    private Contest updateIfMoreConfident(Contest contest, ContestDraft draft) {
        if (!contest.isDeadlineConfirmed() && draft.deadlineConfirmed && draft.deadline != null) {
            contest.setDeadline(draft.deadline);
            contest.setDeadlineConfirmed(true);
        }
        if (!contest.isEligibilityConfirmed() && draft.eligibilityConfirmed && draft.eligibility != null) {
            contest.setEligibility(draft.eligibility);
            contest.setEligibilityConfirmed(true);
        }
        if (contest.getPrizeAmountKrw() == null && draft.prizeAmountKrw != null) {
            contest.setPrizeAmountKrw(draft.prizeAmountKrw);
            contest.setPrizeDescription(draft.prizeDescription);
        }
        if (contest.getIndividualOrTeam() == IndividualOrTeam.UNKNOWN && draft.individualOrTeam != IndividualOrTeam.UNKNOWN) {
            contest.setIndividualOrTeam(draft.individualOrTeam);
        }
        if (contest.getOnlineOffline() == OnlineOffline.UNKNOWN && draft.onlineOffline != OnlineOffline.UNKNOWN) {
            contest.setOnlineOffline(draft.onlineOffline);
        }
        return contest;
    }

    private void applyDraft(Contest contest, ContestDraft draft) {
        contest.setTitle(draft.title != null ? draft.title : "확인 필요");
        contest.setOrganizer(draft.organizer);
        contest.setCategoryList(draft.categories);
        contest.setDescription(draft.description);
        contest.setEligibility(draft.eligibility);
        contest.setEligibilityConfirmed(draft.eligibilityConfirmed);
        contest.setIndividualOrTeam(draft.individualOrTeam);
        contest.setApplicationStart(draft.applicationStart);
        contest.setDeadline(draft.deadline);
        contest.setDeadlineConfirmed(draft.deadlineConfirmed);
        contest.setPrizeDescription(draft.prizeDescription);
        contest.setPrizeAmountKrw(draft.prizeAmountKrw);
        contest.setRegion(draft.region);
        contest.setOnlineOffline(draft.onlineOffline);
        contest.setSubmissionItems(draft.submissionItems);
        contest.setOfficialUrl(draft.officialUrl);
        contest.setConfidence(draft.confidence);
        contest.setStatus(draft.deadlineConfirmed && draft.eligibilityConfirmed
                ? ContestStatus.VERIFIED
                : ContestStatus.NEEDS_REVIEW);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
