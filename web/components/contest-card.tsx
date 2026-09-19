"use client";

import { Bookmark, CalendarDays, MapPin } from "lucide-react";
import type { Contest, FeedbackType } from "@/lib/api";
import { contestBanner, daysUntil } from "@/lib/contest-visuals";

export function ContestCard({
  contest,
  active,
  score,
  currentFeedback,
  onSelect,
  onToggleSave,
}: {
  contest: Contest;
  active: boolean;
  score?: number | null;
  currentFeedback?: FeedbackType | null;
  onSelect: () => void;
  onToggleSave: () => void;
}) {
  const category = contest.categoryList[0] ?? "일반";
  const organizer = contest.organizer ?? "주최기관 확인 필요";
  const days = daysUntil(contest.deadline);
  const saved = currentFeedback === "SAVED" || currentFeedback === "PLAN_TO_APPLY" || currentFeedback === "APPLIED";

  return (
    <article
      onClick={onSelect}
      className={`mb-3 cursor-pointer overflow-hidden rounded-2xl border transition ${
        active ? "border-green-200 bg-green-50 shadow-sm" : "border-slate-100 hover:bg-slate-50"
      }`}
    >
      <div className="relative h-28 w-full overflow-hidden bg-slate-100">
        <img src={contestBanner(contest.title, category, organizer)} alt="" className="h-full w-full object-cover" />
        {days !== null && (
          <span className="pointer-events-none absolute left-2 top-2 rounded-md bg-white/95 px-2 py-1 text-[11px] font-black text-slate-800 shadow">
            {days >= 0 ? `D-${days}` : "마감"}
          </span>
        )}
        {typeof score === "number" && (
          <span className="pointer-events-none absolute left-2 bottom-2 rounded-md bg-emerald-600/95 px-2 py-1 text-[11px] font-black text-white shadow">
            추천 {score}점
          </span>
        )}
        <button
          type="button"
          aria-label="저장"
          onClick={(event) => {
            event.stopPropagation();
            onToggleSave();
          }}
          className={`absolute right-2 top-2 grid h-7 w-7 place-items-center rounded-full bg-white/95 shadow ${
            saved ? "text-[#03a94d]" : "text-slate-400"
          }`}
        >
          <Bookmark size={15} fill={saved ? "currentColor" : "none"} />
        </button>
      </div>
      <div className="p-4">
        <div className="flex items-center gap-1.5">
          <span className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-bold text-slate-500">{category}</span>
          {contest.status === "NEEDS_REVIEW" && (
            <span className="rounded-full bg-amber-100 px-2 py-1 text-[11px] font-bold text-amber-700">확인 필요</span>
          )}
        </div>
        <p className="mt-2 line-clamp-1 text-[15px] font-black text-slate-900">{contest.title}</p>
        <p className="mt-1 text-xs font-bold text-slate-400">{organizer}</p>
        <div className="mt-3 flex items-center justify-between text-[11px] font-bold text-slate-400">
          <span><MapPin className="mr-1 inline" size={13} />{contest.region ?? "전국"}</span>
          <span><CalendarDays className="mr-1 inline" size={13} />{contest.deadline ?? "확인 필요"}</span>
        </div>
      </div>
    </article>
  );
}
