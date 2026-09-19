"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Bookmark, CalendarDays, Compass, ExternalLink, MapPin, Search, X } from "lucide-react";
import type { CrawledContest, ContestFormat } from "./api/contests/route";

type Coordinate = { lat: number; lng: number };
type Contest = CrawledContest;
type MapsWindow = Window & { google?: any; contestMateMapsLoader?: Promise<void> };

const fallbackContests: Contest[] = [
  { id: "online-1", title: "2026 대학생 AI 아이디어 공모전", host: "서울 AI 허브", category: "AI", tags: ["AI", "온라인"], deadlineDate: "2026-10-15", deadlineLabel: "10월 15일", location: "온라인 진행", format: "online", summary: "AI로 바꾸는 일상을 주제로 아이디어를 모집합니다.", image: "", url: "https://example.com" },
  { id: "online-2", title: "청년 공공데이터 분석 챌린지", host: "열린데이터광장", category: "데이터", tags: ["데이터", "온라인"], deadlineDate: "2026-10-05", deadlineLabel: "10월 5일", location: "온라인 진행", format: "online", summary: "데이터 기반 문제 해결 아이디어를 제안하세요.", image: "", url: "https://example.com" },
  { id: "offline-1", title: "대학생 앱 개발 해커톤", host: "서울창업허브", category: "IT·개발", tags: ["개발", "오프라인"], deadlineDate: "2026-10-18", deadlineLabel: "10월 18일", location: "서울특별시 마포구 백범로 31길 21", format: "offline", summary: "행사 장소에서 진행되는 48시간 개발 해커톤입니다.", image: "", url: "https://example.com" },
  { id: "offline-2", title: "그린 모빌리티 디자인 공모전", host: "서울디자인재단", category: "디자인", tags: ["디자인", "오프라인"], deadlineDate: "2026-11-01", deadlineLabel: "11월 1일", location: "서울특별시 동대문구 을지로 281", format: "offline", summary: "현장 전시와 심사가 포함된 디자인 공모전입니다.", image: "", url: "https://example.com" },
];
const quickTags = ["전체", "AI", "기획", "디자인", "개발", "데이터", "영상"];
const radiusOptions = [3, 5, 10, 20, 30, 50, 100, 200, 500];
const REGION_COORDS: [string, Coordinate][] = [
  ["서울", { lat: 37.5665, lng: 126.978 }], ["부산", { lat: 35.1796, lng: 129.0756 }],
  ["대구", { lat: 35.8714, lng: 128.6014 }], ["인천", { lat: 37.4563, lng: 126.7052 }],
  ["광주", { lat: 35.1595, lng: 126.8526 }], ["대전", { lat: 36.3504, lng: 127.3845 }],
  ["울산", { lat: 35.5384, lng: 129.3114 }], ["세종", { lat: 36.4801, lng: 127.289 }],
  ["경기", { lat: 37.4138, lng: 127.5183 }], ["강원", { lat: 37.8228, lng: 128.1555 }],
  ["충북", { lat: 36.6357, lng: 127.4917 }], ["충남", { lat: 36.5184, lng: 126.8 }],
  ["전북", { lat: 35.7175, lng: 127.153 }], ["전남", { lat: 34.8161, lng: 126.463 }],
  ["경북", { lat: 36.4919, lng: 128.8889 }], ["경남", { lat: 35.4606, lng: 128.2132 }], ["제주", { lat: 33.4996, lng: 126.5312 }],
];

const daysUntil = (date: string) => Math.ceil((new Date(date).getTime() - new Date().setHours(0, 0, 0, 0)) / 86400000);
const dday = (date: string) => { const days = daysUntil(date); return days < 0 ? "마감" : days === 0 ? "D-Day" : `D-${days}`; };
const distanceKm = (a: Coordinate, b: Coordinate) => {
  const r = 6371; const dLat = (b.lat - a.lat) * Math.PI / 180; const dLng = (b.lng - a.lng) * Math.PI / 180;
  const value = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * Math.PI / 180) * Math.cos(b.lat * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(value));
};
// Used only when Google cannot resolve a region. Never scatter pins around a
// province/city centroid: an approximate pin must remain at that area's centre.
const fallbackCoordinateForLocation = (location: string): Coordinate | null => {
  const region = REGION_COORDS.find(([name]) => location.includes(name));
  return region?.[1] ?? null;
};
const escapeHtml = (text: string) => text.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" })[char] as string);
const markerIcon = (number: number, current = false) => `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="48" height="62" viewBox="0 0 48 62"><path d="M24 3C12.4 3 3 12.4 3 24c0 16.5 21 35 21 35s21-18.5 21-35C45 12.4 35.6 3 24 3Z" fill="${current ? "#2563eb" : "#03c75a"}" stroke="white" stroke-width="3"/><circle cx="24" cy="24" r="12" fill="white"/><text x="24" y="28" text-anchor="middle" font-family="Arial" font-size="12" font-weight="700" fill="${current ? "#2563eb" : "#03a94d"}">${current ? "나" : number}</text></svg>`)}`;

function ContestMap({ contests, radiusKm, onCoordinate, onLocation }: { contests: Contest[]; radiusKm: number; onCoordinate: (id: string, point: Coordinate | null) => void; onLocation: (point: Coordinate, located: boolean) => void }) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const centerRef = useRef<Coordinate | null>(null);
  const locatedRef = useRef(false);
  const cache = useRef(new Map<string, Coordinate | null>());
  const onLocationRef = useRef(onLocation);
  onLocationRef.current = onLocation;
  const [message, setMessage] = useState("지도 준비 중…");
  const [mapReady, setMapReady] = useState(false);
  const [mapCenter, setMapCenter] = useState<Coordinate | null>(null);
  const [centerName, setCenterName] = useState("");

  const requestLocation = useCallback(() => {
    const fallback = { lat: 37.5665, lng: 126.978 };
    const settle = (point: Coordinate, located: boolean) => {
      centerRef.current = point; locatedRef.current = located; onLocationRef.current(point, located);
      mapRef.current?.setCenter(point); setMapCenter(point);
      setMessage(located ? "현재 위치를 기준으로 찾고 있어요." : "위치 권한이 없어 서울시청을 기준으로 표시합니다.");
    };
    if (!navigator.geolocation) { settle(fallback, false); return; }
    navigator.geolocation.getCurrentPosition(
      (position) => settle({ lat: position.coords.latitude, lng: position.coords.longitude }, true),
      () => settle(fallback, false),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }, []);

  const changeSearchCenter = useCallback((name: string) => {
    setCenterName(name);
    if (!name) { requestLocation(); return; }
    const region = REGION_COORDS.find(([regionName]) => regionName === name);
    if (!region) return;
    const point = region[1];
    centerRef.current = point; locatedRef.current = false; onLocationRef.current(point, false);
    mapRef.current?.setCenter(point); mapRef.current?.setZoom(9); setMapCenter(point);
    setMessage(`${name} ${radiusKm}km ${"\uBC18\uACBD\uC744 \uAE30\uC900\uC73C\uB85C \uAC80\uC0C9\uD569\uB2C8\uB2E4."}`);
  }, [radiusKm, requestLocation]);

  useEffect(() => {
    let cancelled = false;
    const boot = async () => {
      try {
        const config = await fetch("/api/maps-config");
        if (!config.ok) throw new Error("Google Maps API 키가 설정되지 않았습니다.");
        const { apiKey } = await config.json();
        const mapsWindow = window as MapsWindow;
        mapsWindow.contestMateMapsLoader ??= new Promise<void>((resolve, reject) => {
          const script = document.createElement("script");
          script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&v=weekly`;
          script.async = true; script.onload = () => resolve(); script.onerror = () => reject(new Error("Google 지도를 불러오지 못했습니다."));
          document.head.appendChild(script);
        });
        await mapsWindow.contestMateMapsLoader;
        if (cancelled || !container.current || !mapsWindow.google) return;
        mapRef.current = new mapsWindow.google.maps.Map(container.current, { center: centerRef.current ?? { lat: 36.4, lng: 127.8 }, zoom: 7, disableDefaultUI: true, zoomControl: true, streetViewControl: false });
        setMapReady(true); requestLocation();
      } catch (error) { if (!cancelled) setMessage(error instanceof Error ? error.message : "지도를 표시하지 못했습니다."); }
    };
    void boot(); return () => { cancelled = true; };
  }, [requestLocation]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || !mapCenter) return;
    const maps = (window as MapsWindow).google.maps;
    const map = mapRef.current; const center = mapCenter;
    const circle = new maps.Circle({ map, center, radius: radiusKm * 1000, fillColor: "#03c75a", fillOpacity: 0.1, strokeColor: "#03c75a", strokeOpacity: 0.8, strokeWeight: 2 });
    const currentMarker = new maps.Marker({ map, position: center, title: "현재 위치", icon: markerIcon(0, true) });
    const markers: any[] = [currentMarker];
    const geocoder = new maps.Geocoder(); const info = new maps.InfoWindow();
    contests.forEach((contest, index) => {
      const show = (point: Coordinate | null) => {
        onCoordinate(contest.id, point);
        if (!point || distanceKm(center, point) > radiusKm) return;
        const marker = new maps.Marker({ map, position: point, title: contest.title, icon: markerIcon(index + 1) }); markers.push(marker);
        const locationLabel = contest.locationPrecision === "exact" ? "정확 주소" : "지역 중심";
        marker.addListener("click", () => { info.setContent(`<div style="max-width:220px;font-family:Arial,sans-serif"><b>${escapeHtml(contest.title)}</b><p style="margin:6px 0;color:#64748b;font-size:12px">${escapeHtml(contest.location)} · ${locationLabel}</p><a target="_blank" rel="noopener noreferrer" href="${escapeHtml(contest.url)}" style="color:#03a94d;font-size:12px;font-weight:bold">공고 보기</a></div>`); info.open({ map, anchor: marker }); });
      };
      if (cache.current.has(contest.location)) { show(cache.current.get(contest.location) ?? null); return; }
      geocoder.geocode({ address: contest.location, region: "KR" }, (entries: any[], status: string) => {
        // Full road addresses resolve to the venue. A district/city string
        // resolves to its geographic centre, matching the displayed precision.
        const point = status === "OK" && entries[0]
          ? { lat: entries[0].geometry.location.lat(), lng: entries[0].geometry.location.lng() }
          : fallbackCoordinateForLocation(contest.location);
        cache.current.set(contest.location, point); show(point);
      });
    });
    setMessage(`${locatedRef.current ? "현재 위치" : "서울시청"} 기준 ${radiusKm}km 내 오프라인 행사만 표시합니다.`);
    return () => { circle.setMap(null); markers.forEach((marker) => marker.setMap(null)); };
  }, [contests, mapCenter, mapReady, onCoordinate, radiusKm]);

  return (
    <section className="relative h-full min-h-[520px] overflow-hidden bg-emerald-50">
      <div ref={container} className="absolute inset-0" aria-label="Offline contest map" />
      <div className="absolute left-5 top-5 z-10 max-w-[280px] rounded-xl bg-white px-4 py-3 shadow-lg">
        <p className="text-xs font-black text-[#03a94d]">{"\uC624\uD504\uB77C\uC778 \uD589\uC0AC \uC9C0\uB3C4"}</p>
        <p className="mt-1 text-sm font-bold leading-5 text-slate-700">{message}</p>
        <label className="mt-3 flex items-center gap-2 text-[11px] font-bold text-slate-500"><span>{"\uAC80\uC0C9 \uC911\uC2EC"}</span><select value={centerName} onChange={(event) => changeSearchCenter(event.target.value)} className="min-w-0 flex-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-bold text-slate-700"><option value="">{"\uD604\uC7AC \uC704\uCE58"}</option>{REGION_COORDS.map(([name]) => <option key={name} value={name}>{name}</option>)}</select></label>
        <div className="mt-3 flex flex-wrap items-center gap-1.5">
          <span className="mr-1 text-[11px] font-bold text-slate-400">{"\uAC80\uC0C9 \uBC18\uACBD"}</span>
          {radiusOptions.map((value) => <button key={value} type="button" onClick={() => window.dispatchEvent(new CustomEvent("contest-radius", { detail: value }))} className={`rounded-md px-2 py-1 text-xs font-bold ${radiusKm === value ? "bg-[#03c75a] text-white" : "bg-slate-100 text-slate-600"}`}>{value}km</button>)}
        </div>
      </div>
      <button type="button" onClick={requestLocation} className="absolute right-5 top-5 z-10 flex items-center gap-2 rounded-xl bg-white px-3 py-2.5 text-xs font-bold text-slate-700 shadow-lg"><Compass size={16} className="text-[#03a94d]" />{"\uD604\uC7AC \uC704\uCE58"}</button>
    </section>
  );

  return <section className="relative min-h-[520px] h-full overflow-hidden bg-emerald-50"><div ref={container} className="absolute inset-0" aria-label="오프라인 공모전 지도" /><div className="absolute left-5 top-5 z-10 max-w-[260px] rounded-xl bg-white px-4 py-3 shadow-lg"><p className="text-xs font-black text-[#03a94d]">오프라인 행사 지도</p><p className="mt-1 text-sm font-bold leading-5 text-slate-700">{message}</p><label className="mt-3 flex items-center gap-2 text-xs font-bold text-slate-500">검색 반경 <select value={radiusKm} onChange={(event) => { const value = Number(event.target.value); }} className="rounded-md border border-slate-200 bg-white px-2 py-1 text-slate-700"><option>{radiusKm}km</option></select></label></div><button type="button" onClick={requestLocation} className="absolute right-5 top-5 z-10 flex items-center gap-2 rounded-xl bg-white px-3 py-2.5 text-xs font-bold text-slate-700 shadow-lg"><Compass size={16} className="text-[#03a94d]" />현재 위치</button></section>;
}

export default function Home() {
  const [contests, setContests] = useState<Contest[]>(fallbackContests);
  const [mode, setMode] = useState<ContestFormat>("online");
  const [resultCount, setResultCount] = useState(250);
  const [submittedTopic, setSubmittedTopic] = useState("");
  const [searchVersion, setSearchVersion] = useState(0);
  const [query, setQuery] = useState(""); const [tag, setTag] = useState("전체"); const [radiusKm, setRadiusKm] = useState(100);
  const [saved, setSaved] = useState<string[]>([]); const [savedOnly, setSavedOnly] = useState(false); const [coordinates, setCoordinates] = useState<Record<string, Coordinate | null>>({});
  const [center, setCenter] = useState<Coordinate | null>(null); const [located, setLocated] = useState(false); const [status, setStatus] = useState("loading");

  useEffect(() => { let cancelled = false; void (async () => { try { const response = await fetch("/api/contests"); const data = await response.json() as { contests?: CrawledContest[] }; if (!cancelled && data.contests?.length) { setContests(data.contests); setStatus("live"); } else if (!cancelled) setStatus("fallback"); } catch { if (!cancelled) setStatus("fallback"); } })(); return () => { cancelled = true; }; }, []);
  useEffect(() => {
    if (searchVersion === 0) return;
    let cancelled = false;
    setStatus("loading");
    void (async () => {
      try {
        const params = new URLSearchParams({ limit: String(resultCount) });
        if (submittedTopic) params.set("topic", submittedTopic);
        const response = await fetch(`/api/contests?${params}`);
        const data = await response.json() as { contests?: CrawledContest[] };
        if (!cancelled && Array.isArray(data.contests)) {
          setContests(data.contests);
          setStatus("live");
        } else if (!cancelled) setStatus("fallback");
      } catch { if (!cancelled) setStatus("fallback"); }
    })();
    return () => { cancelled = true; };
  }, [resultCount, searchVersion, submittedTopic]);
  useEffect(() => {
    // The original sidebar is a static one-line starter layout. Mount this
    // focused control group into its header so visitors can choose both the
    // subject to collect and the amount to return before a new crawl begins.
    const header = document.querySelector("aside header");
    if (!header || document.getElementById("contest-collection-controls")) return;
    const panel = document.createElement("form");
    panel.id = "contest-collection-controls";
    panel.className = "mt-3 grid grid-cols-[1fr_auto] gap-2 rounded-xl border border-emerald-100 bg-emerald-50 p-2";
    const topic = document.createElement("input");
    topic.type = "search";
    topic.placeholder = "주제 대상 (예: AI, 디자인, 마케팅)";
    topic.className = "min-w-0 rounded-lg border border-emerald-200 bg-white px-3 py-2 text-sm font-semibold outline-none";
    topic.setAttribute("aria-label", "수집할 공모전 주제");
    const count = document.createElement("select");
    count.className = "rounded-lg border border-emerald-200 bg-white px-2 py-2 text-sm font-bold text-slate-700";
    count.setAttribute("aria-label", "표시할 검색 결과 수");
    [100, 250, 500].forEach((value) => { const option = document.createElement("option"); option.value = String(value); option.textContent = `${value}개`; if (value === resultCount) option.selected = true; count.append(option); });
    const button = document.createElement("button");
    button.type = "submit"; button.textContent = "다시 수집";
    button.className = "col-span-2 rounded-lg bg-[#03a94d] px-3 py-2 text-sm font-black text-white";
    panel.append(topic, count, button);
    const submit = (event: Event) => {
      event.preventDefault();
      const nextTopic = topic.value.trim();
      const nextCount = Number(count.value);
      setQuery(nextTopic); setSubmittedTopic(nextTopic); setResultCount(nextCount); setSearchVersion((value) => value + 1);
    };
    panel.addEventListener("submit", submit);
    header.append(panel);
    return () => { panel.removeEventListener("submit", submit); panel.remove(); };
  }, [resultCount]);
  useEffect(() => { const listener = (event: Event) => setRadiusKm(Number((event as CustomEvent<number>).detail)); window.addEventListener("contest-radius", listener); return () => window.removeEventListener("contest-radius", listener); }, []);
  const matchText = useCallback((contest: Contest) => { const haystack = [contest.title, contest.host, contest.category, contest.location, ...contest.tags].join(" ").toLowerCase(); return (!query || haystack.includes(query.toLowerCase())) && (tag === "전체" || contest.category.includes(tag) || contest.tags.includes(tag)) && (!savedOnly || saved.includes(contest.id)); }, [query, saved, savedOnly, tag]);
  const online = useMemo(() => contests.filter((contest) => contest.format === "online" && matchText(contest)), [contests, matchText]);
  const offlineCandidates = useMemo(() => contests.filter((contest) => contest.format === "offline" && matchText(contest)), [contests, matchText]);
  // Keep text search independent from map geocoding and the selected map radius.
  // Otherwise a valid nationwide result can disappear while its address is being
  // resolved, or simply because it is outside the user's nearby-map radius.
  const offline = offlineCandidates;
  const displayed = mode === "online" ? online : offline;
  const setPoint = useCallback((id: string, point: Coordinate | null) => setCoordinates((previous) => previous[id] === point ? previous : { ...previous, [id]: point }), []);
  const updateRadius = (value: number) => setRadiusKm(value);

  return <main className="min-h-screen bg-slate-100 text-slate-900 lg:flex lg:h-screen lg:overflow-hidden"><aside className="z-10 flex w-full shrink-0 flex-col bg-white shadow-[8px_0_30px_rgba(15,23,42,.10)] lg:max-w-[430px]"><header className="border-b border-slate-100 px-6 pb-4 pt-5"><div className="mb-5 flex items-center justify-between"><div className="flex items-center gap-2.5"><span className="grid h-9 w-9 place-items-center rounded-xl bg-[#03c75a] font-black text-white">M</span><span className="text-lg font-black tracking-tight">MatchUp</span></div><button onClick={() => setSavedOnly((value) => !value)} className={`rounded-full px-3 py-1.5 text-xs font-bold ${savedOnly ? "bg-emerald-100 text-[#038a40]" : "bg-slate-100 text-slate-500"}`}>저장 {saved.length}</button></div><nav className="grid grid-cols-2 gap-2 rounded-xl bg-slate-100 p-1"><button type="button" onClick={() => setMode("online")} className={`rounded-lg px-3 py-2 text-sm font-black ${mode === "online" ? "bg-white text-[#03a94d] shadow-sm" : "text-slate-400"}`}>온라인 공모전</button><button type="button" onClick={() => setMode("offline")} className={`rounded-lg px-3 py-2 text-sm font-black ${mode === "offline" ? "bg-white text-[#03a94d] shadow-sm" : "text-slate-400"}`}>오프라인 행사</button></nav><div className="mt-4 flex items-center gap-2 rounded-xl border-2 border-[#03c75a] px-3 py-3"><Search size={20} className="text-[#03a94d]" /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={mode === "online" ? "온라인 공모전 검색" : "오프라인 행사 검색"} className="min-w-0 flex-1 bg-transparent text-[15px] font-semibold outline-none" />{query && <button onClick={() => setQuery("")} aria-label="검색어 지우기"><X size={18} /></button>}</div><div className="mt-3 flex gap-2 overflow-x-auto pb-1">{quickTags.map((item) => <button key={item} type="button" onClick={() => setTag(item)} className={`shrink-0 rounded-full px-3 py-2 text-xs font-bold ${tag === item ? "bg-[#03c75a] text-white" : "bg-slate-100 text-slate-600"}`}>{item}</button>)}</div></header><div className="flex items-center justify-between px-6 py-4"><p className="text-sm font-bold text-slate-500"><b className="text-slate-900">{displayed.length}개</b> {mode === "online" ? "온라인 공모전" : `반경 ${radiusKm}km 내 오프라인 행사`}</p>{status !== "live" && <span className="text-xs font-bold text-amber-600">예시 데이터</span>}</div><div className="min-h-0 flex-1 overflow-y-auto px-4 pb-5">{mode === "offline" && !located && <p className="mb-3 rounded-xl bg-blue-50 px-3 py-2 text-xs font-bold leading-5 text-blue-700">위치 권한을 허용하면 현재 위치 기준으로 다시 표시합니다.</p>}{mode === "offline" && offlineCandidates.length > 0 && displayed.length === 0 && <p className="mb-3 rounded-xl bg-slate-100 px-3 py-2 text-xs font-bold leading-5 text-slate-600">장소를 확인하고 있어요. 주소를 찾을 수 있는 행사만 지도와 목록에 표시합니다.</p>}{displayed.map((contest, index) => <article key={contest.id} className="mb-3 overflow-hidden rounded-2xl border border-slate-100 bg-white"><div className="p-4"><div className="flex items-start justify-between gap-3"><div><span className={`rounded-full px-2 py-1 text-[11px] font-black ${contest.format === "online" ? "bg-blue-50 text-blue-700" : "bg-emerald-50 text-[#038a40]"}`}>{contest.format === "online" ? "온라인" : "오프라인"}</span><h2 className="mt-2 text-[15px] font-black leading-5">{contest.title}</h2></div><button type="button" aria-label="저장" onClick={() => setSaved((items) => items.includes(contest.id) ? items.filter((id) => id !== contest.id) : [...items, contest.id])} className={saved.includes(contest.id) ? "text-[#03a94d]" : "text-slate-400"}><Bookmark size={19} fill={saved.includes(contest.id) ? "currentColor" : "none"} /></button></div><p className="mt-2 text-xs font-bold text-slate-400">{contest.host}</p><p className="mt-2 text-xs leading-5 text-slate-500">{contest.summary}</p><div className="mt-3 flex items-center justify-between gap-2 text-[11px] font-bold text-slate-400"><span className="min-w-0 truncate"><MapPin className="mr-1 inline" size={13} />{contest.location}</span><span className="shrink-0"><CalendarDays className="mr-1 inline" size={13} />{dday(contest.deadlineDate)}</span></div><a href={contest.url} target="_blank" rel="noopener noreferrer" className="mt-3 inline-flex items-center gap-1 text-xs font-black text-[#038a40]"><ExternalLink size={13} />공고 보기</a></div></article>)}</div></aside><section className="min-h-[520px] flex-1">{mode === "offline" ? <ContestMap contests={offlineCandidates} radiusKm={radiusKm} onCoordinate={setPoint} onLocation={(point, isLocated) => { setCenter(point); setLocated(isLocated); }} /> : <div className="grid h-full min-h-[520px] place-items-center bg-gradient-to-br from-blue-50 to-emerald-50 p-8 text-center"><div><div className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-white text-[#03a94d] shadow-sm"><Search size={28} /></div><h2 className="mt-5 text-xl font-black">온라인 공모전을 찾는 중이에요</h2><p className="mt-2 max-w-sm text-sm font-semibold leading-6 text-slate-500">온라인 공모전은 장소와 거리 제한 없이 별도로 검색할 수 있습니다.</p></div></div>}</section></main>;
}
