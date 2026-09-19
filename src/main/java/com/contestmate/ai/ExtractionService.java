package com.contestmate.ai;

import com.contestmate.collector.RawDocument;
import com.contestmate.contest.Confidence;
import com.contestmate.contest.IndividualOrTeam;
import com.contestmate.contest.OnlineOffline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a RawDocument into a ContestDraft. Prefers the configured LLM (structured JSON
 * output); when the LLM is not configured or fails to return valid JSON, falls back to a
 * keyword/regex heuristic so the pipeline still produces usable (if lower-confidence) data
 * without any GPU dependency. Never guesses a deadline or eligibility it cannot find text
 * evidence for -- those stay unconfirmed ("확인 필요") instead.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private static final String SYSTEM_PROMPT =
            "You extract structured contest/hackathon listing data from Korean or English announcement text. " +
            "Respond with ONLY a JSON object matching this schema, no prose: " +
            "{\"title\":string,\"organizer\":string|null,\"categories\":string[],\"description\":string," +
            "\"eligibility\":string|null,\"eligibilityConfirmed\":boolean,\"individualOrTeam\":\"INDIVIDUAL\"|\"TEAM\"|\"BOTH\"|\"UNKNOWN\"," +
            "\"applicationStart\":\"YYYY-MM-DD\"|null,\"deadline\":\"YYYY-MM-DD\"|null,\"deadlineConfirmed\":boolean," +
            "\"prizeDescription\":string|null,\"prizeAmountKrw\":number|null,\"region\":string|null," +
            "\"onlineOffline\":\"ONLINE\"|\"OFFLINE\"|\"HYBRID\"|\"UNKNOWN\",\"submissionItems\":string|null}. " +
            "Set eligibilityConfirmed/deadlineConfirmed to false and leave the value null whenever the text is " +
            "ambiguous -- never invent a date or requirement that is not stated.";

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    static {
        CATEGORY_KEYWORDS.put("AI", Arrays.asList("ai", "인공지능", "머신러닝", "딥러닝", "llm"));
        CATEGORY_KEYWORDS.put("Mobile", Arrays.asList("android", "ios", "kotlin", "모바일", "앱"));
        CATEGORY_KEYWORDS.put("Development", Arrays.asList("개발", "development", "programming", "backend", "프로그래밍"));
        CATEGORY_KEYWORDS.put("Data", Arrays.asList("data", "데이터", "분석", "빅데이터"));
        CATEGORY_KEYWORDS.put("Design", Arrays.asList("design", "디자인", "ux", "ui"));
        CATEGORY_KEYWORDS.put("Startup", Arrays.asList("창업", "스타트업", "startup", "비즈니스"));
        CATEGORY_KEYWORDS.put("Hackathon", Arrays.asList("해커톤", "hackathon"));
    }

    private static final Pattern DEADLINE_PATTERN = Pattern.compile(
            "(?:마감|접수\\s*마감|신청\\s*마감|deadline)\\D{0,10}(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
    private static final Pattern PRIZE_PATTERN = Pattern.compile("(\\d[\\d,]*)\\s*만\\s*원");
    private static final Pattern TEAM_PATTERN = Pattern.compile("팀\\s*(구성|참가|단위)|team\\s*(entry|only)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDIVIDUAL_PATTERN = Pattern.compile("개인\\s*(참가|자격)|individual\\s*only", Pattern.CASE_INSENSITIVE);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExtractionService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ContestDraft extract(RawDocument doc) {
        if (llmClient.isConfigured()) {
            ContestDraft draft = tryLlmExtract(doc);
            if (draft != null) {
                return draft;
            }
        }
        return heuristicExtract(doc);
    }

    private ContestDraft tryLlmExtract(RawDocument doc) {
        String userPrompt = "Title: " + doc.getRawTitle() + "\n\nContent:\n" + doc.getRawContent();
        return llmClient.chatComplete(SYSTEM_PROMPT, userPrompt)
                .map(this::parseJsonDraft)
                .orElse(null);
    }

    private ContestDraft parseJsonDraft(String json) {
        try {
            String cleaned = json.trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end < 0) return null;
            JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));

            ContestDraft draft = new ContestDraft();
            draft.title = node.path("title").asText(null);
            draft.organizer = node.path("organizer").asText(null);
            node.path("categories").forEach(c -> draft.categories.add(c.asText()));
            draft.description = node.path("description").asText(null);
            draft.eligibility = node.path("eligibility").asText(null);
            draft.eligibilityConfirmed = node.path("eligibilityConfirmed").asBoolean(false);
            draft.individualOrTeam = parseEnum(node.path("individualOrTeam").asText("UNKNOWN"), IndividualOrTeam.UNKNOWN);
            draft.applicationStart = parseDate(node.path("applicationStart").asText(null));
            draft.deadline = parseDate(node.path("deadline").asText(null));
            draft.deadlineConfirmed = node.path("deadlineConfirmed").asBoolean(false) && draft.deadline != null;
            draft.prizeDescription = node.path("prizeDescription").asText(null);
            draft.prizeAmountKrw = node.path("prizeAmountKrw").isNumber() ? node.path("prizeAmountKrw").asLong() : null;
            draft.region = node.path("region").asText(null);
            draft.onlineOffline = parseEnum(node.path("onlineOffline").asText("UNKNOWN"), OnlineOffline.UNKNOWN);
            draft.submissionItems = node.path("submissionItems").asText(null);
            draft.confidence = Confidence.HIGH;
            return draft;
        } catch (Exception e) {
            log.warn("Failed to parse LLM extraction JSON, falling back to heuristics: {}", e.getMessage());
            return null;
        }
    }

    /** No-LLM fallback: keyword categories + regex deadline/prize/team detection. */
    private ContestDraft heuristicExtract(RawDocument doc) {
        String text = ((doc.getRawTitle() == null ? "" : doc.getRawTitle()) + " " +
                (doc.getRawContent() == null ? "" : doc.getRawContent()));
        String lower = text.toLowerCase();

        ContestDraft draft = new ContestDraft();
        draft.title = doc.getRawTitle();
        draft.description = doc.getRawContent();
        draft.officialUrl = doc.getSourceUrl();
        draft.confidence = Confidence.LOW;

        CATEGORY_KEYWORDS.forEach((category, keywords) -> {
            if (keywords.stream().anyMatch(lower::contains)) {
                draft.categories.add(category);
            }
        });

        Matcher deadlineMatcher = DEADLINE_PATTERN.matcher(text);
        if (deadlineMatcher.find()) {
            try {
                draft.deadline = LocalDate.parse(String.format("%s-%02d-%02d",
                        deadlineMatcher.group(1),
                        Integer.parseInt(deadlineMatcher.group(2)),
                        Integer.parseInt(deadlineMatcher.group(3))));
                draft.deadlineConfirmed = true;
            } catch (Exception ignored) {
                draft.deadlineConfirmed = false;
            }
        } else {
            draft.deadlineConfirmed = false; // "확인 필요" surfaced by caller when null
        }

        Matcher prizeMatcher = PRIZE_PATTERN.matcher(text);
        if (prizeMatcher.find()) {
            draft.prizeAmountKrw = Long.parseLong(prizeMatcher.group(1).replace(",", "")) * 10_000L;
            draft.prizeDescription = prizeMatcher.group(0);
        }

        if (TEAM_PATTERN.matcher(text).find() && INDIVIDUAL_PATTERN.matcher(text).find()) {
            draft.individualOrTeam = IndividualOrTeam.BOTH;
        } else if (TEAM_PATTERN.matcher(text).find()) {
            draft.individualOrTeam = IndividualOrTeam.TEAM;
        } else if (INDIVIDUAL_PATTERN.matcher(text).find()) {
            draft.individualOrTeam = IndividualOrTeam.INDIVIDUAL;
        }

        if (lower.contains("온라인") || lower.contains("online")) {
            draft.onlineOffline = OnlineOffline.ONLINE;
        } else if (lower.contains("오프라인") || lower.contains("offline")) {
            draft.onlineOffline = OnlineOffline.OFFLINE;
        }

        draft.eligibilityConfirmed = false; // heuristic extraction never confirms eligibility text
        return draft;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, T fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
