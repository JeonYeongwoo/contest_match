// Thin client for the MatchUp Spring Boot API. Every call attaches an anonymous X-User-Id
// header (a UUID kept in localStorage) so the backend can persist a profile and feedback
// without collecting any real personal information.

export type IndividualOrTeam = "INDIVIDUAL" | "TEAM" | "BOTH" | "UNKNOWN";
export type OnlineOffline = "ONLINE" | "OFFLINE" | "HYBRID" | "UNKNOWN";
export type Confidence = "LOW" | "MEDIUM" | "HIGH";
export type ContestStatus = "NEEDS_REVIEW" | "VERIFIED";
export type FeedbackType = "NOT_INTERESTED" | "SAVED" | "PLAN_TO_APPLY" | "APPLIED";
export type TeamPreference = "INDIVIDUAL" | "TEAM" | "ANY";
export type PrizePreference = "LOW" | "MEDIUM" | "HIGH" | "ANY";
export type OnlinePreference = "ONLINE" | "OFFLINE" | "ANY";

export type Contest = {
  id: number;
  title: string;
  organizer: string | null;
  categoryList: string[];
  description: string | null;
  eligibility: string | null;
  eligibilityConfirmed: boolean;
  individualOrTeam: IndividualOrTeam;
  applicationStart: string | null;
  deadline: string | null;
  deadlineConfirmed: boolean;
  prizeDescription: string | null;
  prizeAmountKrw: number | null;
  region: string | null;
  onlineOffline: OnlineOffline;
  online: boolean;
  submissionItems: string | null;
  officialUrl: string | null;
  confidence: Confidence;
  status: ContestStatus;
  openForApplication: boolean;
};

export type ContestDetail = {
  contest: Contest;
  sourceUrls: string[];
};

export type Recommendation = {
  contest: Contest;
  score: number;
  matchedInterests: string[];
  reason: string;
  eligibilityWarning: boolean;
  eligibilityWarningText: string | null;
  sourceUrls: string[];
  userFeedback: FeedbackType | null;
};

export type HomeResponse = {
  todaysPicks: Recommendation[];
  deadlineSoon: Contest[];
  popularCategories: string[];
};

export type Profile = {
  interests: string[];
  majorOrJob: string | null;
  region: string | null;
  age: number | null;
  eligibilityNote: string | null;
  teamPreference: TeamPreference;
  prizePreference: PrizePreference;
  deadlinePreferenceDays: number | null;
  onlinePreference: OnlinePreference;
};

export type FeedbackedContest = {
  contest: Contest;
  feedbackType: FeedbackType;
};

export type ContestFilters = {
  region?: string;
  category?: string;
  organizer?: string;
  onlineOnly?: boolean;
  individualOrTeam?: IndividualOrTeam;
  minPrize?: number;
  deadlineWithinDays?: number;
  keyword?: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const USER_ID_STORAGE_KEY = "matchup:userId";

export function getUserId(): string {
  if (typeof window === "undefined") return "";
  let id = window.localStorage.getItem(USER_ID_STORAGE_KEY);
  if (!id) {
    id = crypto.randomUUID();
    window.localStorage.setItem(USER_ID_STORAGE_KEY, id);
  }
  return id;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-User-Id": getUserId(),
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`${response.status} ${response.statusText}: ${body}`);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function fetchContests(filters: ContestFilters): Promise<Contest[]> {
  const params = new URLSearchParams();
  if (filters.region) params.set("region", filters.region);
  if (filters.category) params.set("category", filters.category);
  if (filters.organizer) params.set("organizer", filters.organizer);
  if (filters.onlineOnly) params.set("onlineOnly", "true");
  if (filters.individualOrTeam) params.set("individualOrTeam", filters.individualOrTeam);
  if (filters.minPrize) params.set("minPrize", String(filters.minPrize));
  if (filters.deadlineWithinDays) params.set("deadlineWithinDays", String(filters.deadlineWithinDays));
  if (filters.keyword) params.set("keyword", filters.keyword);
  const query = params.toString();
  return request<Contest[]>(`/api/contests${query ? `?${query}` : ""}`);
}

export function fetchContestDetail(id: number): Promise<ContestDetail> {
  return request<ContestDetail>(`/api/contests/${id}`);
}

export function fetchHome(): Promise<HomeResponse> {
  return request<HomeResponse>("/api/recommendations/home");
}

export function fetchRecommendations(limit = 4): Promise<Recommendation[]> {
  return request<Recommendation[]>("/api/recommendations", {
    method: "POST",
    body: JSON.stringify({ limit }),
  });
}

export function fetchProfile(): Promise<Profile | null> {
  return request<Profile>("/api/profile").catch((error: Error) => {
    if (error.message.startsWith("404")) return null;
    throw error;
  });
}

export function saveProfile(profile: Profile): Promise<Profile> {
  return request<Profile>("/api/profile", {
    method: "POST",
    body: JSON.stringify(profile),
  });
}

export function submitFeedback(contestId: number, feedbackType: FeedbackType) {
  return request<void>("/api/feedback", {
    method: "POST",
    body: JSON.stringify({ contestId, feedbackType }),
  });
}

export function fetchSavedContests(): Promise<FeedbackedContest[]> {
  return request<FeedbackedContest[]>("/api/feedback/contests");
}
