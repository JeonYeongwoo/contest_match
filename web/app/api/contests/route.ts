import { NextResponse } from "next/server";
import * as cheerio from "cheerio";

export type CrawledContest = {
  id: string;
  title: string;
  host: string;
  category: string;
  tags: string[];
  deadlineDate: string;
  deadlineLabel: string;
  location: string;
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
  ["아이디어·기획", ["아이디어", "기획"]],
  ["AI", ["ai", "인공지능"]],
  ["디자인", ["디자인", "웹툰", "캐릭터", "그림", "로고", "슬로건"]],
  ["영상·콘텐츠", ["영상", "ucc", "숏폼", "사진", "콘텐츠", "영화"]],
  ["데이터", ["데이터", "분석", "논문", "리포트"]],
  ["IT·개발", ["sw", "소프트웨어", "개발", "앱", "해커톤", "it", "웹/모바일"]],
];

function guessCategory(title: string): string {
  const lower = title.toLowerCase();
  for (const [category, keywords] of CATEGORY_KEYWORDS) {
    if (keywords.some((keyword) => lower.includes(keyword))) return category;
  }
  return "기타";
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function toDeadlineLabel(date: Date): string {
  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
}

async function fetchHtml(url: string): Promise<string> {
  const response = await fetch(url, { headers: BROWSER_HEADERS, cache: "no-store" });
  if (!response.ok) throw new Error(`${url} 요청 실패 (${response.status})`);
  return response.text();
}

/** 링커리어(linkareer.com) 공모전 목록 페이지에 내장된 __NEXT_DATA__에서 목록을 추출합니다. */
async function fetchLinkareerContests(): Promise<CrawledContest[]> {
  const html = await fetchHtml("https://linkareer.com/list/contest");
  const $ = cheerio.load(html);
  const script = $("#__NEXT_DATA__").text();
  if (!script) throw new Error("linkareer: __NEXT_DATA__를 찾지 못했습니다.");

  const data = JSON.parse(script) as {
    props?: {
      pageProps?: {
        activityItems?: { url: string; name: string; imageUrl: string }[];
        __APOLLO_STATE__?: Record<string, { organizationName?: string; recruitCloseAt?: number }>;
      };
    };
  };
  const activityItems = data.props?.pageProps?.activityItems ?? [];
  const apolloState = data.props?.pageProps?.__APOLLO_STATE__ ?? {};
  if (activityItems.length === 0) throw new Error("linkareer: 공모전 목록이 비어 있습니다.");

  return activityItems.map((item) => {
    const id = item.url.split("/").filter(Boolean).pop() ?? item.name;
    const activity = apolloState[`Activity:${id}`];
    const closeAt = activity?.recruitCloseAt ? new Date(activity.recruitCloseAt) : null;
    const category = guessCategory(item.name);
    return {
      id: `linkareer-${id}`,
      title: item.name,
      host: activity?.organizationName || "링커리어",
      category,
      tags: [category],
      deadlineDate: closeAt ? toIsoDate(closeAt) : toIsoDate(new Date(Date.now() + 14 * 86400000)),
      deadlineLabel: closeAt ? toDeadlineLabel(closeAt) : "상시 모집",
      location: "전국(온라인)",
      summary: `${activity?.organizationName || "주최기관"}에서 진행하는 ${category} 분야 공모전입니다.`,
      image: item.imageUrl,
      url: item.url,
      source: "linkareer",
    } satisfies CrawledContest;
  });
}

export async function GET() {
  const today = toIsoDate(new Date());
  try {
    const contests = (await fetchLinkareerContests())
      .filter((contest) => contest.deadlineDate >= today)
      .sort((a, b) => a.deadlineDate.localeCompare(b.deadlineDate));
    if (contests.length === 0) return NextResponse.json({ contests: [], errors: ["결과가 비어 있습니다."] }, { status: 502 });
    return NextResponse.json({ contests, errors: [] });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return NextResponse.json({ contests: [], errors: [message] }, { status: 502 });
  }
}
