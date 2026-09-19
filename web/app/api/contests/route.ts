import { NextResponse } from "next/server";
import * as cheerio from "cheerio";

export type ContestFormat = "online" | "offline";
export type LocationPrecision = "exact" | "area" | "unknown";
export type ContestSource = "linkareer" | "wevity" | "allcon" | "thinkgood" | "contestkorea" | "allforyoung" | "dev-event";

export type CrawledContest = {
  id: string; title: string; host: string; category: string; tags: string[];
  deadlineDate: string; deadlineLabel: string; location: string; format: ContestFormat;
  /** Exact means a venue/road address was found in the original notice. */
  locationPrecision?: LocationPrecision;
  summary: string; image: string; url: string; source: ContestSource;
};

type Portal = { source: Exclude<ContestSource, "linkareer">; name: string; url: string; detailUrl: (url: string) => boolean };

type SearchOptions = {
  /** The number of notices the visitor wants to see, not an arbitrary UI cap. */
  limit: number;
  /** Optional subject supplied from the MatchUp search box. */
  topic: string;
  /** Sources selected by the visitor; an empty set means every supported source. */
  sources: Set<ContestSource>;
};

const DEFAULT_RESULT_COUNT = 250;
const MAX_RESULT_COUNT = 500;
const MAX_LIST_PAGES_PER_PORTAL = 12;

const BROWSER_HEADERS = { "User-Agent": "MatchUpContestBot/1.0 (+contest recommendation research)", "Accept-Language": "ko-KR,ko;q=0.9" };
const PORTALS: Portal[] = [
  { source: "wevity", name: "Wevity", url: "https://www.wevity.com/", detailUrl: (url) => /[?&]gbn=view/i.test(url) },
  { source: "allcon", name: "All-Con", url: "https://www.all-con.co.kr/", detailUrl: (url) => /contest|view|detail/i.test(url) },
  { source: "thinkgood", name: "Thinkgood", url: "https://www.thinkcontest.com/", detailUrl: (url) => /contest.*detail|view/i.test(url) },
  { source: "contestkorea", name: "Contest Korea", url: "https://www.contestkorea.com/", detailUrl: (url) => /view\.php|egoread\.php/i.test(url) },
  { source: "allforyoung", name: "All For Young", url: "https://www.allforyoung.com/posts/contest", detailUrl: (url) => /\/posts\/contest\/.+/i.test(url) },
];

const CATEGORY_KEYWORDS: [string, string[]][] = [
  ["Idea & Planning", ["idea", "planning", "slogan"]], ["AI", ["ai", "artificial intelligence"]],
  ["Design", ["design", "illustration", "webtoon", "logo"]], ["Video & Content", ["video", "ucc", "film", "photo", "content"]],
  ["Data", ["data", "analysis", "report"]], ["IT & Development", ["software", "coding", "hackathon", "app", "web"]],
];
const ONLINE_WORDS = ["online", "zoom", "webex", "youtube", "remote", "온라인", "비대면", "화상"];
const KOREAN_LOCATION_PATTERN = /((?:서울(?:특별시)?|부산(?:광역시)?|대구(?:광역시)?|인천(?:광역시)?|광주(?:광역시)?|대전(?:광역시)?|울산(?:광역시)?|세종(?:특별자치시)?|제주(?:특별자치도)?|경기도|강원도|충청[남북]도|전라[남북]도|경상[남북]도)(?:\s+[가-힣0-9·-]+){0,7})/;
const cleanText = (value: string) => value.replace(/\s+/g, " ").trim();
const toIsoDate = (date: Date) => date.toISOString().slice(0, 10);
const deadlineFallback = () => toIsoDate(new Date(Date.now() + 30 * 86_400_000));
const guessCategory = (title: string) => CATEGORY_KEYWORDS.find(([, words]) => words.some((word) => title.toLowerCase().includes(word)))?.[0] ?? "Other";

async function fetchHtml(url: string): Promise<string> {
  const response = await fetch(url, { headers: BROWSER_HEADERS, next: { revalidate: 900 } });
  if (!response.ok) throw new Error(`${response.status} ${url}`);
  return response.text();
}

const ROAD_ADDRESS_PATTERN = /(?:서울(?:특별시)?|부산(?:광역시)?|대구(?:광역시)?|인천(?:광역시)?|광주(?:광역시)?|대전(?:광역시)?|울산(?:광역시)?|세종(?:특별자치시)?|경기도|강원(?:특별자치도)?|충청[북남]도|전라[북남]도|경상[북남]도|제주(?:특별자치도)?)\s+(?:(?:[가-힣]+(?:시|군|구))\s+){0,2}[가-힣0-9·-]+(?:로|길)\s*\d+(?:-\d+)?(?:\s*(?:[가-힣0-9]+(?:층|호|동)|\([^)]{1,40}\)))?/g;
const BARE_ROAD_ADDRESS_PATTERN = /[가-힣0-9·-]+(?:로|길)(?:\s+\d+길)?\s+\d+(?:-\d+)?(?:\s*(?:[가-힣0-9]+(?:층|호|동)|\([^)]{1,40}\)))?/g;
const AREA_PATTERN = /(?:서울(?:특별시)?|부산(?:광역시)?|대구(?:광역시)?|인천(?:광역시)?|광주(?:광역시)?|대전(?:광역시)?|울산(?:광역시)?|세종(?:특별자치시)?|경기도|강원(?:특별자치도)?|충청[북남]도|전라[북남]도|경상[북남]도|제주(?:특별자치도)?)(?:\s+[가-힣]+(?:시|군|구))?/;
const VENUE_LABEL_PATTERN = /(?:행사\s*장소|개최\s*장소|진행\s*장소|장소|주소|venue|location)\s*[:：]?\s*([^\n|]{3,140})/ig;

type VenueResult = { location: string; precision: LocationPrecision };

/**
 * Reads a public source notice only when its robots policy permits it. The
 * source's labelled venue/address wins; a road address found in the notice is
 * the next best option. We deliberately keep a region-level fallback instead
 * of inventing a street-level address.
 */
async function extractVenueFromSource(url: string, fallback: string): Promise<VenueResult> {
  try {
    if (!(await isAllowedByRobots(url))) return { location: fallback, precision: "area" };
    const $ = cheerio.load(await fetchHtml(url));
    const labelled = cleanText($("address, [itemprop*='address'], [class*='address' i], [class*='venue' i], [class*='location' i]").text());
    const body = cleanText($("body").text());
    const candidates = [labelled, ...Array.from(body.matchAll(VENUE_LABEL_PATTERN), (match) => cleanText(match[1])), ...Array.from(body.matchAll(ROAD_ADDRESS_PATTERN), (match) => cleanText(match[0]))];
    for (const candidate of candidates) {
      const roadAddress = candidate.match(ROAD_ADDRESS_PATTERN)?.[0] ?? candidate.match(BARE_ROAD_ADDRESS_PATTERN)?.[0];
      if (roadAddress) {
        // Notices often omit the city before a road name. Add only the known
        // area in that case so Google geocoding has an unambiguous address.
        const area = candidate.match(AREA_PATTERN)?.[0] ?? fallback.match(AREA_PATTERN)?.[0];
        return { location: cleanText(roadAddress.match(AREA_PATTERN) ? roadAddress : `${area ?? ""} ${roadAddress}`), precision: "exact" };
      }
    }
    // A labelled locality (for example “서울 강남구”) is useful, but must be
    // displayed as an area centre rather than pretending to be an exact venue.
    const locality = candidates.map((candidate) => candidate.match(AREA_PATTERN)?.[0]).find(Boolean);
    if (locality) return { location: locality, precision: "area" };
  } catch { /* source pages are optional; retain the verified list location */ }
  return { location: fallback, precision: fallback ? "area" : "unknown" };
}

/** Applies public User-agent:* disallow rules to the list path before collecting it. */
async function isAllowedByRobots(url: string): Promise<boolean> {
  const target = new URL(url);
  try {
    const response = await fetch(`${target.origin}/robots.txt`, { headers: BROWSER_HEADERS, next: { revalidate: 3600 } });
    if (response.status === 404) return true;
    if (!response.ok) return false;
    let wildcard = false;
    for (const raw of (await response.text()).split(/\r?\n/)) {
      const rule = raw.trim();
      if (!rule || rule.startsWith("#")) continue;
      const [key, ...rest] = rule.split(":"); const value = rest.join(":").trim();
      if (key.toLowerCase() === "user-agent") wildcard = value === "*";
      if (wildcard && key.toLowerCase() === "disallow" && value && target.pathname.startsWith(value)) return false;
    }
    return true;
  } catch { return false; }
}

const isPaginationUrl = (url: string) => /(?:[?&](?:page|pageno|pageNo|pageIndex|pg|p)=|\/page\/\d+)/i.test(url);

/**
 * Starts from each portal's public list, then follows only same-site pagination
 * links. This deliberately does not turn a search into an unrestricted crawl.
 */
async function fetchPortalContests(portal: Portal, maxItems: number): Promise<CrawledContest[]> {
  if (!(await isAllowedByRobots(portal.url))) return [];
  const seenDetails = new Set<string>();
  const seenLists = new Set<string>();
  const listQueue = [portal.url];
  const contests: CrawledContest[] = [];

  while (listQueue.length && seenLists.size < MAX_LIST_PAGES_PER_PORTAL && contests.length < maxItems) {
    const listUrl = listQueue.shift()!;
    if (seenLists.has(listUrl) || !(await isAllowedByRobots(listUrl))) continue;
    seenLists.add(listUrl);
    const $ = cheerio.load(await fetchHtml(listUrl));
    $("a[href]").each((_, anchor) => {
      const href = $(anchor).prop("href"); if (!href) return;
      let url: string;
      try {
        url = new URL(href, listUrl).toString();
        if (new URL(url).host !== new URL(portal.url).host) return;
      } catch { return; }

      if (isPaginationUrl(url) && !seenLists.has(url) && !listQueue.includes(url) && listQueue.length < MAX_LIST_PAGES_PER_PORTAL) {
        listQueue.push(url);
      }
      if (!portal.detailUrl(url) || seenDetails.has(url) || contests.length >= maxItems) return;
      const title = cleanText($(anchor).text()) || cleanText($(anchor).closest("article, li, div").text()).slice(0, 160);
      if (title.length < 8 || title.length > 160 || /^(login|home|all|notice|more|privacy)/i.test(title)) return;
      seenDetails.add(url);
      const category = guessCategory(title);
      contests.push({ id: `${portal.source}-${contests.length}-${encodeURIComponent(url).slice(-24)}`, title, host: portal.name, category,
        tags: [category, "Online"], deadlineDate: deadlineFallback(), deadlineLabel: "See source", location: "Online / see source", format: "online",
        summary: `Listed by ${portal.name}. Check the original notice for eligibility and deadlines.`, image: "", url, source: portal.source });
    });
  }
  return contests;
}

async function inspectLinkareerFormat(url: string, title: string): Promise<{ format: ContestFormat; location: string }> {
  try {
    const text = cleanText(cheerio.load(await fetchHtml(url))("body").text());
    const location = text.match(KOREAN_LOCATION_PATTERN)?.[1] ?? text.match(/(Seoul|Busan|Daegu|Incheon|Daejeon|Gwangju|Ulsan|Jeju)[^\n]{0,50}/i)?.[0];
    if (location) return { format: "offline", location };
    if (ONLINE_WORDS.some((word) => `${title} ${text}`.toLowerCase().includes(word))) return { format: "online", location: "Online" };
  } catch { /* a source detail failure must not remove the list item */ }
  return { format: "online", location: "Location not confirmed" };
}

async function fetchLinkareerContests(maxItems: number): Promise<CrawledContest[]> {
  const listUrl = "https://linkareer.com/list/contest";
  if (!(await isAllowedByRobots(listUrl))) return [];
  const $ = cheerio.load(await fetchHtml(listUrl)); const script = $("#__NEXT_DATA__").text();
  if (!script) return [];
  const data = JSON.parse(script) as { props?: { pageProps?: { activityItems?: { url: string; name: string; imageUrl: string }[]; __APOLLO_STATE__?: Record<string, { organizationName?: string; recruitCloseAt?: number }> } } };
  const state = data.props?.pageProps?.__APOLLO_STATE__ ?? {}; const items = (data.props?.pageProps?.activityItems ?? []).slice(0, maxItems);
  return Promise.all(items.map(async (item) => {
    const rawId = item.url.split("/").filter(Boolean).pop() ?? item.name; const activity = state[`Activity:${rawId}`];
    const closeAt = activity?.recruitCloseAt ? new Date(activity.recruitCloseAt) : null; const detail = await inspectLinkareerFormat(item.url, item.name); const category = guessCategory(item.name);
    return { id: `linkareer-${rawId}`, title: item.name, host: activity?.organizationName || "Linkareer", category,
      tags: [category, detail.format === "online" ? "Online" : "Offline"], deadlineDate: closeAt ? toIsoDate(closeAt) : deadlineFallback(),
      deadlineLabel: closeAt ? `${closeAt.getMonth() + 1}/${closeAt.getDate()}` : "See source", location: detail.location, format: detail.format,
      locationPrecision: detail.format === "offline" ? (/(?:로|길)\s*\d/.test(detail.location) ? "exact" : "area") : "unknown",
      summary: "Check the original notice for eligibility and deadlines.", image: item.imageUrl, url: item.url, source: "linkareer" satisfies ContestSource };
  }));
}

/**
 * Dev-Event maintains a public, structured Markdown list of developer events.
 * Unlike a generic contest portal, its `오프라인(지역)` classification gives us a
 * verified map-search location without guessing from a title or banner image.
 */
async function fetchDevEventOfflineContests(maxItems: number): Promise<CrawledContest[]> {
  const markdown = await fetchHtml("https://raw.githubusercontent.com/brave-people/Dev-Event/master/README.md");
  const events: CrawledContest[] = [];
  const pattern = /__\[([^\]]+)\]\((https?:\/\/[^)]+)\)__\s*-\s*분류:\s*([^\n]+?)\s*-\s*주최:\s*([^\n]+?)\s*-\s*(?:접수|일시):\s*([^\n]+?)(?=\s+-\s+__\[|\s+##|$)/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(markdown)) !== null && events.length < maxItems) {
    const [, title, url, classification, organizer = "Dev-Event", schedule = ""] = match;
    const locations = Array.from(classification.matchAll(/오프라인\(([^)]+)\)/g), (location) => cleanText(location[1]));
    for (const [index, location] of locations.entries()) {
      const category = guessCategory(title);
      events.push({
        id: `dev-event-${events.length}-${index}`,
        title: cleanText(title),
        host: cleanText(organizer),
        category,
        tags: [category, "Offline"],
        deadlineDate: deadlineFallback(),
        deadlineLabel: cleanText(schedule) || "See source",
        location,
        format: "offline",
        summary: `Dev-Event verified location: ${location}. ${cleanText(classification.replace(/`/g, ""))}`,
        image: "",
        url,
        source: "dev-event",
      });
    }
  }
  // Several entries can share one source page. Cache the inspection and cap
  // concurrent detail requests so source sites are not flooded.
  const venueByUrl = new Map<string, Promise<VenueResult>>();
  let cursor = 0;
  const workers = Array.from({ length: Math.min(4, events.length) }, async () => {
    while (cursor < events.length) {
      const event = events[cursor++];
      const venue = venueByUrl.get(event.url) ?? extractVenueFromSource(event.url, event.location);
      venueByUrl.set(event.url, venue);
      const resolved = await venue;
      event.location = resolved.location;
      event.locationPrecision = resolved.precision;
      event.summary = `${resolved.precision === "exact" ? "Original notice venue address" : "Original notice area"}: ${resolved.location}. ${event.summary}`;
    }
  });
  await Promise.all(workers);
  return events;
}

function searchOptions(request: Request): SearchOptions {
  const params = new URL(request.url).searchParams;
  const requestedLimit = Number.parseInt(params.get("limit") ?? String(DEFAULT_RESULT_COUNT), 10);
  const limit = Number.isFinite(requestedLimit) ? Math.min(Math.max(requestedLimit, 1), MAX_RESULT_COUNT) : DEFAULT_RESULT_COUNT;
  const topic = (params.get("topic") ?? "").trim().slice(0, 80).toLowerCase();
  const supportedSources = new Set<ContestSource>(["linkareer", "wevity", "allcon", "thinkgood", "contestkorea", "allforyoung", "dev-event"]);
  const sources = new Set((params.get("sources") ?? "").split(",").filter((source): source is ContestSource => supportedSources.has(source as ContestSource)));
  return { limit, topic, sources };
}

export async function GET(request: Request) {
  const options = searchOptions(request);
  const useSource = (source: ContestSource) => options.sources.size === 0 || options.sources.has(source);
  const perSource = Math.ceil(options.limit / Math.max(options.sources.size || 7, 1));
  const jobs: Promise<CrawledContest[]>[] = [];
  if (useSource("linkareer")) jobs.push(fetchLinkareerContests(perSource));
  if (useSource("dev-event")) jobs.push(fetchDevEventOfflineContests(perSource));
  for (const portal of PORTALS) if (useSource(portal.source)) jobs.push(fetchPortalContests(portal, perSource));
  const results = await Promise.allSettled(jobs);
  const contests = results.flatMap((result) => result.status === "fulfilled" ? result.value : []);
  const matchesTopic = (contest: CrawledContest) => !options.topic || [contest.title, contest.host, contest.category, contest.summary, ...contest.tags].join(" ").toLowerCase().includes(options.topic);
  const unique = Array.from(new Map(contests.filter(matchesTopic).map((contest) => [contest.id, contest])).values())
    .sort((a, b) => a.deadlineDate.localeCompare(b.deadlineDate))
    .slice(0, options.limit);
  const errors = results.flatMap((result) => result.status === "rejected" ? [result.reason instanceof Error ? result.reason.message : "source unavailable"] : []);
  return NextResponse.json({ contests: unique, errors, requestedCount: options.limit, returnedCount: unique.length });
}
