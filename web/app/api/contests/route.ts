import { NextResponse } from "next/server";
import * as cheerio from "cheerio";

export type ContestFormat = "online" | "offline";

export type CrawledContest = {
  id: string;
  title: string;
  host: string;
  category: string;
  tags: string[];
  deadlineDate: string;
  deadlineLabel: string;
  location: string;
  format: ContestFormat;
  summary: string;
  image: string;
  url: string;
  source: "linkareer";
};

const BROWSER_HEADERS = {
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36",
  "Accept-Language": "ko-KR,ko;q=0.9",
};
const CATEGORY_KEYWORDS: [string, string[]][] = [
  ["아이디어·기획", ["아이디어", "기획"]], ["AI", ["ai", "인공지능"]],
  ["디자인", ["디자인", "웹툰", "캐릭터", "그림", "로고", "브로슈어"]],
  ["영상·콘텐츠", ["영상", "ucc", "숏폼", "사진", "콘텐츠", "영화"]],
  ["데이터", ["데이터", "분석", "논문", "리포트"]],
  ["IT·개발", ["sw", "소프트웨어", "개발", "코딩", "해커톤", "it", "앱", "모바일"]],
];
const ONLINE_WORDS = ["온라인", "비대면", "zoom", "줌", "webex", "유튜브", "온라인 접수", "원격"];
const ADDRESS_PATTERN = /((?:서울(?:특별시|시)?|부산(?:광역시)?|대구(?:광역시)?|인천(?:광역시)?|광주(?:광역시)?|대전(?:광역시)?|울산(?:광역시)?|세종(?:특별자치시)?|경기(?:도)?|강원(?:특별자치도|도)?|충청[남북]도?|전라[남북]도?|경상[남북]도?|제주(?:특별자치도)?)\s*(?:[가-힣0-9·()\-]+\s*){0,7}(?:구|군|시|읍|면|동|로|길|역|대학교|대학|캠퍼스|센터|홀|관|코엑스))/;

function guessCategory(title: string): string {
  const lower = title.toLowerCase();
  return CATEGORY_KEYWORDS.find(([, words]) => words.some((word) => lower.includes(word)))?.[0] ?? "기타";
}
function toIsoDate(date: Date): string { return date.toISOString().slice(0, 10); }
function toDeadlineLabel(date: Date): string { return `${date.getMonth() + 1}월 ${date.getDate()}일`; }
function cleanText(value: string): string { return value.replace(/\s+/g, " ").trim(); }

async function fetchHtml(url: string): Promise<string> {
  const response = await fetch(url, { headers: BROWSER_HEADERS, next: { revalidate: 900 } });
  if (!response.ok) throw new Error(`공고 요청 실패 (${response.status})`);
  return response.text();
}

/** 상세 공고의 본문/배너에 적힌 장소만 사용한다. 추측 좌표는 만들지 않는다. */
async function inspectFormat(url: string, title: string): Promise<{ format: ContestFormat; location: string }> {
  try {
    const $ = cheerio.load(await fetchHtml(url));
    const text = cleanText($("body").text());
    const place = text.match(ADDRESS_PATTERN)?.[1]?.trim();
    if (place) return { format: "offline", location: place };
    if (ONLINE_WORDS.some((word) => `${title} ${text}`.toLowerCase().includes(word))) return { format: "online", location: "온라인 진행" };
  } catch {
    // 상세를 읽지 못한 공고는 온라인 목록에 남기고 지도에서는 제외한다.
  }
  return { format: "online", location: "온라인/장소 미확인" };
}

async function fetchLinkareerContests(): Promise<CrawledContest[]> {
  const $ = cheerio.load(await fetchHtml("https://linkareer.com/list/contest"));
  const script = $("#__NEXT_DATA__").text();
  if (!script) throw new Error("공고 목록 데이터를 찾지 못했습니다.");
  const data = JSON.parse(script) as { props?: { pageProps?: { activityItems?: { url: string; name: string; imageUrl: string }[]; __APOLLO_STATE__?: Record<string, { organizationName?: string; recruitCloseAt?: number }> } } };
  const items = (data.props?.pageProps?.activityItems ?? []).slice(0, 16);
  const state = data.props?.pageProps?.__APOLLO_STATE__ ?? {};
  if (!items.length) throw new Error("현재 공고 목록이 비어 있습니다.");

  return Promise.all(items.map(async (item) => {
    const rawId = item.url.split("/").filter(Boolean).pop() ?? item.name;
    const activity = state[`Activity:${rawId}`];
    const closeAt = activity?.recruitCloseAt ? new Date(activity.recruitCloseAt) : null;
    const detail = await inspectFormat(item.url, item.name);
    const category = guessCategory(item.name);
    return {
      id: `linkareer-${rawId}`, title: item.name, host: activity?.organizationName || "링커리어", category,
      tags: [category, detail.format === "online" ? "온라인" : "오프라인"],
      deadlineDate: closeAt ? toIsoDate(closeAt) : toIsoDate(new Date(Date.now() + 14 * 86400000)),
      deadlineLabel: closeAt ? toDeadlineLabel(closeAt) : "상시 모집",
      location: detail.location, format: detail.format,
      summary: `${activity?.organizationName || "주최기관"} 공고입니다. 상세 페이지에서 참가 조건을 확인하세요.`,
      image: item.imageUrl, url: item.url, source: "linkareer",
    } satisfies CrawledContest;
  }));
}

export async function GET() {
  const today = toIsoDate(new Date());
  try {
    const contests = (await fetchLinkareerContests()).filter((contest) => contest.deadlineDate >= today).sort((a, b) => a.deadlineDate.localeCompare(b.deadlineDate));
    return NextResponse.json({ contests, errors: [] });
  } catch (error) {
    return NextResponse.json({ contests: [], errors: [error instanceof Error ? error.message : "공고를 불러오지 못했습니다."] }, { status: 502 });
  }
}
