"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Bookmark, CalendarDays, Compass, ExternalLink, Filter, MapPin, Search, SlidersHorizontal, X } from "lucide-react";
import type { CrawledContest } from "./api/contests/route";

type Contest = { id: string; title: string; host: string; category: string; deadline: string; deadlineDate: string; location: string; summary: string; tags: string[]; coordinate: { lat: number; lng: number }; url: string; image: string };
type MapsWindow = Window & { google?: any; contestMateMapsLoader?: Promise<void> };

const bannerTheme: Record<string, [string, string]> = {
  "아이디어·기획": ["#03c75a", "#03a94d"],
  "데이터": ["#2563eb", "#1e40af"],
  "영상·콘텐츠": ["#f97316", "#c2410c"],
  "디자인": ["#a855f7", "#7e22ce"],
  "IT·개발": ["#06b6d4", "#0e7490"],
};
const wrapTitle = (text: string, maxLen: number) => {
  const words = text.split(" ");
  const lines: string[] = [];
  let line = "";
  words.forEach((word) => {
    if ((line + " " + word).trim().length > maxLen) { lines.push(line.trim()); line = word; }
    else { line = (line + " " + word).trim(); }
  });
  if (line) lines.push(line);
  return lines.slice(0, 3);
};
/** 크롤링한 배너 이미지가 없거나 로드에 실패했을 때 쓰는 대체 배너입니다. */
const placeholderBanner = (contest: Pick<Contest, "title" | "category" | "host">) => {
  const [from, to] = bannerTheme[contest.category] ?? ["#64748b", "#334155"];
  const lines = wrapTitle(contest.title, 11);
  const titleSvg = lines.map((line, index) => `<text x="24" y="${76 + index * 32}" font-family="Arial,sans-serif" font-size="23" font-weight="800" fill="white">${line}</text>`).join("");
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="400" height="220" viewBox="0 0 400 220"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${from}"/><stop offset="1" stop-color="${to}"/></linearGradient></defs><rect width="400" height="220" fill="url(#g)"/>${titleSvg}<text x="24" y="196" font-family="Arial,sans-serif" font-size="14" font-weight="700" fill="white" opacity="0.85">${contest.host}</text></svg>`)}`;
};
const daysUntil = (dateStr: string) => {
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const target = new Date(dateStr); target.setHours(0, 0, 0, 0);
  return Math.round((target.getTime() - today.getTime()) / 86400000);
};
const ddayLabel = (dateStr: string) => { const days = daysUntil(dateStr); return days < 0 ? "마감" : days === 0 ? "D-Day" : `D-${days}`; };
const distanceKm = (a: { lat: number; lng: number }, b: { lat: number; lng: number }) => {
  const R = 6371;
  const dLat = (b.lat - a.lat) * (Math.PI / 180);
  const dLng = (b.lng - a.lng) * (Math.PI / 180);
  const sinLat = Math.sin(dLat / 2);
  const sinLng = Math.sin(dLng / 2);
  const h = sinLat * sinLat + Math.cos(a.lat * (Math.PI / 180)) * Math.cos(b.lat * (Math.PI / 180)) * sinLng * sinLng;
  return 2 * R * Math.asin(Math.sqrt(h));
};
const REGION_COORDS: [string, { lat: number; lng: number }][] = [
  ["제주", { lat: 33.4996, lng: 126.5312 }],
  ["부산", { lat: 35.1796, lng: 129.0756 }],
  ["대구", { lat: 35.8714, lng: 128.6014 }],
  ["인천", { lat: 37.4563, lng: 126.7052 }],
  ["광주", { lat: 35.1595, lng: 126.8526 }],
  ["대전", { lat: 36.3504, lng: 127.3845 }],
  ["울산", { lat: 35.5384, lng: 129.3114 }],
  ["세종", { lat: 36.4801, lng: 127.289 }],
  ["강원", { lat: 37.8228, lng: 128.1555 }],
  ["충북", { lat: 36.6357, lng: 127.4917 }],
  ["충남", { lat: 36.5184, lng: 126.8 }],
  ["전북", { lat: 35.7175, lng: 127.153 }],
  ["전남", { lat: 34.8161, lng: 126.463 }],
  ["경북", { lat: 36.4919, lng: 128.8889 }],
  ["경남", { lat: 35.4606, lng: 128.2132 }],
  ["경기", { lat: 37.4138, lng: 127.5183 }],
  ["서울", { lat: 37.5665, lng: 126.978 }],
];
const seedFor = (id: string) => { let seed = 0; for (let i = 0; i < id.length; i += 1) seed = (seed * 31 + id.charCodeAt(i)) >>> 0; return seed; };
const scatter = (seed: number, base: { lat: number; lng: number }, minRadius: number, maxRadius: number) => {
  const angle = (seed % 360) * (Math.PI / 180);
  const radius = minRadius + ((seed >>> 8) % 100) / 100 * (maxRadius - minRadius);
  return { lat: base.lat + Math.sin(angle) * radius, lng: base.lng + Math.cos(angle) * radius };
};
/** 크롤링 결과에는 실제 주소가 없어, 제목에 포함된 지역명을 찾아 그 지역 근사 좌표에 배치하고, 못 찾으면 서울 중심부 근방에 흩뿌립니다. */
const coordinateFor = (id: string, text: string) => {
  const seed = seedFor(id);
  const matched = REGION_COORDS.find(([name]) => text.includes(name));
  return matched ? scatter(seed, matched[1], 0.004, 0.02) : scatter(seed, { lat: 37.5665, lng: 126.978 }, 0.015, 0.065);
};
const toContest = (item: CrawledContest): Contest => ({
  id: item.id, title: item.title, host: item.host, category: item.category, deadline: item.deadlineLabel,
  deadlineDate: item.deadlineDate, location: item.location, summary: item.summary, tags: item.tags,
  coordinate: coordinateFor(item.id, `${item.title} ${item.host}`), url: item.url, image: item.image,
});

const fallbackContests: Contest[] = [
  { id: "fallback-1", title: "2026 서울 AI 아이디어 공모전", host: "서울 AI 허브", category: "아이디어·기획", deadline: "10월 15일", deadlineDate: "2026-10-15", location: "서울 강남구", summary: "AI로 바꾸는 서울의 일상, 시민 아이디어를 기다립니다.", tags: ["AI", "기획", "서울"], coordinate: { lat: 37.4979, lng: 127.0276 }, url: "https://example.com/contest/ai-idea", image: "" },
  { id: "fallback-2", title: "청년 공공데이터 분석 챌린지", host: "서울 열린데이터광장", category: "데이터", deadline: "10월 5일", deadlineDate: "2026-10-05", location: "서울 중구", summary: "도시 문제를 해결할 데이터 기반 서비스 제안 공모전입니다.", tags: ["데이터", "분석", "서울"], coordinate: { lat: 37.564, lng: 126.981 }, url: "https://example.com/contest/youth-data", image: "" },
  { id: "fallback-3", title: "K-콘텐츠 숏폼 공모전", host: "한국콘텐츠진흥원", category: "영상·콘텐츠", deadline: "10월 25일", deadlineDate: "2026-10-25", location: "서울 마포구", summary: "나만의 시선으로 K-컬처를 60초 영상에 담아보세요.", tags: ["영상", "콘텐츠", "창작"], coordinate: { lat: 37.5565, lng: 126.9237 }, url: "https://example.com/contest/kcontent-shortform", image: "" },
  { id: "fallback-4", title: "그린 모빌리티 디자인 공모전", host: "서울디자인재단", category: "디자인", deadline: "11월 1일", deadlineDate: "2026-11-01", location: "서울 동대문구", summary: "지속 가능한 이동 경험을 위한 디자인 아이디어를 찾습니다.", tags: ["디자인", "모빌리티", "기획"], coordinate: { lat: 37.5665, lng: 127.0092 }, url: "https://example.com/contest/green-mobility", image: "" },
  { id: "fallback-5", title: "대학생 앱 개발 해커톤", host: "서울창업허브", category: "IT·개발", deadline: "10월 18일", deadlineDate: "2026-10-18", location: "서울 마포구", summary: "48시간 동안 팀과 함께 문제를 해결하는 앱을 만들어보세요.", tags: ["개발", "앱", "해커톤"], coordinate: { lat: 37.5489, lng: 126.948 }, url: "https://example.com/contest/app-hackathon", image: "" },
];

const quickTags = ["전체", "AI", "기획", "디자인", "개발", "데이터", "영상"];
const pin = (number: number, current = false) => `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="48" height="62" viewBox="0 0 48 62"><path d="M24 3C12.4 3 3 12.4 3 24c0 16.5 21 35 21 35s21-18.5 21-35C45 12.4 35.6 3 24 3Z" fill="${current ? "#2563eb" : "#03c75a"}" stroke="white" stroke-width="3"/><circle cx="24" cy="24" r="12" fill="white"/><text x="24" y="28" text-anchor="middle" font-family="Arial,sans-serif" font-size="12" font-weight="700" fill="${current ? "#2563eb" : "#03a94d"}">${current ? "나" : number}</text></svg>`)}`;
const escapeHtml = (text: string) => text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c] as string);
const infoWindowContent = (contest: Contest) => {
  const link = /^https?:\/\//i.test(contest.url)
    ? `<a href="${escapeHtml(contest.url)}" target="_blank" rel="noopener noreferrer" style="display:inline-block;margin-top:6px;font-size:12px;font-weight:700;color:#2563eb;text-decoration:none;">사이트로 이동 →</a>`
    : "";
  return `<div style="min-width:200px;max-width:230px;font-family:Arial,sans-serif;">
    <p style="margin:0 0 4px;font-size:11px;font-weight:700;color:#03a94d;">${escapeHtml(contest.category)} · ${ddayLabel(contest.deadlineDate)}</p>
    <p style="margin:0 0 4px;font-size:14px;font-weight:800;color:#0f172a;line-height:1.3;">${escapeHtml(contest.title)}</p>
    <p style="margin:0 0 2px;font-size:12px;color:#64748b;">${escapeHtml(contest.host)}</p>
    <p style="margin:0;font-size:12px;color:#64748b;">${escapeHtml(contest.location)}</p>
    ${link}
  </div>`;
};

const radiusOptions = [3, 5, 10, 20, 30, 50];

function ContestMap({ results, selectedId, onSelect, onLocation, radiusKm, onRadiusChange }: { results: Contest[]; selectedId: string | null; onSelect: (id: string) => void; onLocation?: (center: { lat: number; lng: number }, located: boolean) => void; radiusKm: number; onRadiusChange: (km: number) => void }) {
  const element = useRef<HTMLDivElement>(null);
  const [message, setMessage] = useState("지도를 불러오는 중입니다.");
  const [myLocation, setMyLocation] = useState<{ lat: number; lng: number } | null>(null);
  const centerRef = useRef<{ lat: number; lng: number } | null>(null);
  const locatedRef = useRef(false);
  useEffect(() => {
    let canceled = false;
    const load = async () => {
      try {
        const response = await fetch("/api/maps-config");
        if (!response.ok) throw new Error("지도 API 키가 설정되지 않았습니다.");
        const { apiKey } = await response.json();
        const mapsWindow = window as MapsWindow;
        mapsWindow.contestMateMapsLoader ??= new Promise<void>((resolve, reject) => {
          const script = document.createElement("script"); script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&v=weekly`; script.async = true;
          script.onload = () => resolve(); script.onerror = () => reject(new Error("Google 지도 스크립트를 불러오지 못했습니다.")); document.head.appendChild(script);
        });
        await mapsWindow.contestMateMapsLoader;
        if (canceled || !element.current || !mapsWindow.google) return;
        const fallback = { lat: 37.5665, lng: 126.978 };
        const render = (center: { lat: number; lng: number }, located: boolean) => {
          if (canceled || !element.current) return;
          const map = new mapsWindow.google.maps.Map(element.current, { center, zoom: 12, disableDefaultUI: true, zoomControl: true, mapTypeControl: false, streetViewControl: false });
          const infoWindow = new mapsWindow.google.maps.InfoWindow();
          new mapsWindow.google.maps.Circle({ map, center, radius: radiusKm * 1000, fillColor: "#03c75a", fillOpacity: 0.1, strokeColor: "#03c75a", strokeOpacity: 0.75, strokeWeight: 2 });
          new mapsWindow.google.maps.Marker({ map, position: center, title: "현재 위치", icon: pin(0, true) });
          results.forEach((contest, index) => {
            const marker = new mapsWindow.google.maps.Marker({ map, position: contest.coordinate, title: contest.title, icon: pin(index + 1) });
            marker.addListener("click", () => {
              onSelect(contest.id);
              infoWindow.setContent(infoWindowContent(contest));
              infoWindow.open({ map, anchor: marker });
            });
          });
          setMyLocation(located ? center : null); setMessage(located ? `현재 위치 기준 ${radiusKm}km 반경을 표시합니다.` : `현재 위치 권한이 없어 서울 시청 기준 ${radiusKm}km 반경을 표시합니다.`);
        };
        if (centerRef.current) { render(centerRef.current, locatedRef.current); return; }
        const settle = (center: { lat: number; lng: number }, located: boolean) => {
          centerRef.current = center; locatedRef.current = located;
          onLocation?.(center, located);
          render(center, located);
        };
        navigator.geolocation?.getCurrentPosition((position) => settle({ lat: position.coords.latitude, lng: position.coords.longitude }, true), () => settle(fallback, false), { enableHighAccuracy: true, timeout: 8000 }) ?? settle(fallback, false);
      } catch (error) { if (!canceled) setMessage(error instanceof Error ? error.message : "지도를 표시하지 못했습니다."); }
    };
    void load(); return () => { canceled = true; };
  }, [results, onSelect, onLocation, radiusKm]);
  return <section className="relative h-full min-h-[520px] overflow-hidden bg-emerald-50"><div ref={element} className="absolute inset-0" aria-label="공모전 위치 지도" /><div className="absolute left-6 top-6 z-10 rounded-xl bg-white px-4 py-3 shadow-lg"><p className="text-xs font-bold text-[#03a94d]">현재 위치 주변</p><p className="mt-1 text-sm font-bold text-slate-700">{message}</p><div className="mt-2 flex items-center gap-2"><span className="text-[11px] font-bold text-slate-400">검색 반경</span><select value={radiusKm} onChange={(event) => onRadiusChange(Number(event.target.value))} className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-bold text-slate-700 outline-none">{radiusOptions.map((km) => <option key={km} value={km}>{km}km</option>)}</select></div></div><button type="button" onClick={() => myLocation && setMessage(`현재 위치 기준 ${radiusKm}km 반경을 표시합니다.`)} className="absolute right-6 top-6 z-10 flex items-center gap-2 rounded-xl bg-white px-3 py-2.5 text-xs font-bold text-slate-700 shadow-lg"><Compass size={16} className="text-[#03a94d]" />내 위치</button></section>;
}

export default function Home() {
  const [contests, setContests] = useState<Contest[]>(fallbackContests);
  const [loadState, setLoadState] = useState<"loading" | "live" | "fallback">("loading");
  const [query, setQuery] = useState(""); const [activeTag, setActiveTag] = useState("전체"); const [savedTab, setSavedTab] = useState(false); const [selectedId, setSelectedId] = useState<string | null>(null); const [saved, setSaved] = useState<string[]>([]);
  const [center, setCenter] = useState<{ lat: number; lng: number } | null>(null);
  const [radiusKm, setRadiusKm] = useState(10);
  const handleLocation = useCallback((nextCenter: { lat: number; lng: number }) => setCenter(nextCenter), []);

  useEffect(() => {
    let canceled = false;
    (async () => {
      try {
        const response = await fetch("/api/contests");
        const data = (await response.json()) as { contests?: CrawledContest[] };
        if (canceled) return;
        if (data.contests && data.contests.length > 0) { setContests(data.contests.map(toContest)); setLoadState("live"); }
        else { setLoadState("fallback"); }
      } catch { if (!canceled) setLoadState("fallback"); }
    })();
    return () => { canceled = true; };
  }, []);

  const results = useMemo(() => contests.filter((contest) => { const text = [contest.title, contest.host, contest.category, contest.location, ...contest.tags].join(" ").toLowerCase(); return (!center || distanceKm(contest.coordinate, center) <= radiusKm) && (!query || text.includes(query.toLowerCase())) && (activeTag === "전체" || contest.tags.includes(activeTag) || contest.category.includes(activeTag)) && (!savedTab || saved.includes(contest.id)); }), [contests, query, activeTag, savedTab, saved, center, radiusKm]);
  const selected = contests.find((contest) => contest.id === selectedId) ?? results[0] ?? contests[0]; const toggleSaved = (id: string) => setSaved((items) => items.includes(id) ? items.filter((item) => item !== id) : [...items, id]);
  return <main className="flex min-h-screen bg-slate-100 text-slate-900 lg:h-screen lg:overflow-hidden"><aside className="z-20 flex w-full shrink-0 flex-col bg-white shadow-[8px_0_30px_rgba(15,23,42,.10)] lg:max-w-[430px]"><header className="border-b border-slate-100 px-6 pb-4 pt-5"><div className="mb-5 flex items-center justify-between"><div className="flex items-center gap-2.5"><span className="grid h-9 w-9 place-items-center rounded-xl bg-[#03c75a] font-black text-white">M</span><span className="text-lg font-black tracking-tight">MatchUp</span></div><SlidersHorizontal size={20} className="text-slate-400" /></div><nav className="flex gap-6 text-sm font-bold"><button type="button" onClick={() => setSavedTab(false)} className={`border-b-2 pb-3 ${!savedTab ? "border-[#03c75a] text-[#03a94d]" : "border-transparent text-slate-400"}`}>공모전 검색</button><button type="button" onClick={() => setSavedTab(true)} className={`border-b-2 pb-3 ${savedTab ? "border-[#03c75a] text-[#03a94d]" : "border-transparent text-slate-400"}`}>저장한 공모전 <span className="ml-1 rounded-full bg-slate-100 px-1.5 py-0.5 text-xs">{saved.length}</span></button></nav><div className="mt-4 flex items-center gap-2 rounded-xl border-2 border-[#03c75a] bg-white px-3.5 py-3 shadow-sm"><Search size={21} className="shrink-0 text-[#03a94d]" /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="공모전, 분야, 주최기관 검색" className="min-w-0 flex-1 bg-transparent text-[15px] font-semibold outline-none placeholder:text-slate-400" />{query && <button type="button" onClick={() => setQuery("")} className="text-slate-400"><X size={19} /></button>}</div><div className="mt-3 flex items-center gap-2 overflow-x-auto pb-1 [scrollbar-width:none]"><span className="flex shrink-0 items-center gap-1 rounded-full border border-slate-200 px-3 py-2 text-xs font-bold text-slate-600"><Filter size={14} />필터</span>{quickTags.map((tag) => <button key={tag} type="button" onClick={() => setActiveTag(tag)} className={`shrink-0 rounded-full px-3 py-2 text-xs font-bold ${activeTag === tag ? "bg-[#03c75a] text-white" : "bg-slate-100 text-slate-600"}`}>{tag}</button>)}</div></header><div className="flex items-center justify-between px-6 py-4"><p className="text-sm font-bold text-slate-500"><b className="text-slate-900">{results.length}개</b>의 공모전을 찾았어요{loadState === "loading" && <span className="ml-2 text-xs font-bold text-slate-400">실시간으로 불러오는 중…</span>}{loadState === "fallback" && <span className="ml-2 text-xs font-bold text-amber-600">실시간 데이터를 가져오지 못해 예시를 표시 중이에요</span>}</p><span className="text-xs font-bold text-[#03a94d]">마감임박순</span></div><div className="min-h-0 flex-1 overflow-y-auto px-4 pb-5">{results.map((contest, index) => <article key={contest.id} onClick={() => setSelectedId(contest.id)} className={`mb-3 cursor-pointer overflow-hidden rounded-2xl border transition ${selected.id === contest.id ? "border-green-200 bg-green-50 shadow-sm" : "border-slate-100 hover:bg-slate-50"}`}><div className="relative"><a href={contest.url} target="_blank" rel="noopener noreferrer" onClick={(event) => event.stopPropagation()} className="group block h-32 w-full overflow-hidden bg-slate-100" aria-label={`${contest.title} 공모전 사이트로 이동`}><img src={contest.image || placeholderBanner(contest)} onError={(event) => { event.currentTarget.onerror = null; event.currentTarget.src = placeholderBanner(contest); }} alt={`${contest.title} 배너`} className="h-full w-full object-cover transition group-hover:scale-105" /><span className="absolute inset-0 hidden items-center justify-center bg-black/30 group-hover:flex"><span className="flex items-center gap-1 rounded-full bg-white/95 px-3 py-1.5 text-xs font-black text-slate-800"><ExternalLink size={13} />사이트로 이동</span></span></a><span className="pointer-events-none absolute left-2 top-2 rounded-md bg-white/95 px-2 py-1 text-[11px] font-black text-slate-800 shadow">{ddayLabel(contest.deadlineDate)}</span><button type="button" aria-label="저장" onClick={(event) => { event.stopPropagation(); toggleSaved(contest.id); }} className={`absolute right-2 top-2 grid h-7 w-7 place-items-center rounded-full bg-white/95 shadow ${saved.includes(contest.id) ? "text-[#03a94d]" : "text-slate-400"}`}><Bookmark size={15} fill={saved.includes(contest.id) ? "currentColor" : "none"} /></button></div><div className="p-4"><div className="flex items-center gap-1.5"><span className="grid h-5 w-5 shrink-0 place-items-center rounded-full bg-[#03c75a] text-[10px] font-black text-white">{index + 1}</span><span className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-bold text-slate-500">{contest.category}</span></div><a href={contest.url} target="_blank" rel="noopener noreferrer" onClick={(event) => event.stopPropagation()} className="mt-2 line-clamp-1 block text-[15px] font-black hover:underline">{contest.title}</a><p className="mt-1 text-xs font-bold text-slate-400">{contest.host}</p><p className="mt-2 line-clamp-1 text-xs leading-5 text-slate-500">{contest.summary}</p><div className="mt-3 flex items-center justify-between text-[11px] font-bold text-slate-400"><span><MapPin className="mr-1 inline" size={13} />{contest.location}</span><span><CalendarDays className="mr-1 inline" size={13} />{contest.deadline}</span></div></div></article>)}</div></aside><div className="min-h-[520px] flex-1"><ContestMap results={results} selectedId={selectedId} onSelect={setSelectedId} onLocation={handleLocation} radiusKm={radiusKm} onRadiusChange={setRadiusKm} /></div></main>;
}
