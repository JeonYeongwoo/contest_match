"use client";

import { AlertTriangle, Calendar, ExternalLink, MapPin, Trophy, Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { Contest, FeedbackType } from "@/lib/api";

const TEAM_LABEL: Record<string, string> = {
  INDIVIDUAL: "개인 참가",
  TEAM: "팀 참가",
  BOTH: "개인/팀 모두 가능",
  UNKNOWN: "확인 필요",
};

const MODE_LABEL: Record<string, string> = {
  ONLINE: "온라인",
  OFFLINE: "오프라인",
  HYBRID: "온/오프라인 병행",
  UNKNOWN: "확인 필요",
};

const FEEDBACK_BUTTONS: { type: FeedbackType; label: string }[] = [
  { type: "NOT_INTERESTED", label: "관심없음" },
  { type: "SAVED", label: "저장" },
  { type: "PLAN_TO_APPLY", label: "지원 예정" },
  { type: "APPLIED", label: "지원 완료" },
];

export function ContestDetailPanel({
  contest,
  sourceUrls,
  reason,
  score,
  eligibilityWarning,
  eligibilityWarningText,
  matchedInterests,
  currentFeedback,
  onFeedback,
}: {
  contest: Contest;
  sourceUrls: string[];
  reason?: string | null;
  score?: number | null;
  eligibilityWarning?: boolean;
  eligibilityWarningText?: string | null;
  matchedInterests?: string[];
  currentFeedback?: FeedbackType | null;
  onFeedback: (type: FeedbackType) => void;
}) {
  const showEligibilityWarning = eligibilityWarning ?? !contest.eligibilityConfirmed;

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-white">
      <div className="border-b border-slate-100 px-6 py-5">
        <div className="flex flex-wrap items-center gap-1.5">
          {contest.categoryList.map((category) => (
            <Badge key={category} variant="secondary">{category}</Badge>
          ))}
          {contest.status === "NEEDS_REVIEW" && (
            <Badge variant="outline" className="border-amber-300 text-amber-700">확인 필요</Badge>
          )}
        </div>
        <h2 className="mt-3 text-xl font-black text-slate-900">{contest.title}</h2>
        <p className="mt-1 text-sm font-bold text-slate-400">{contest.organizer ?? "주최기관 확인 필요"}</p>

        {typeof score === "number" && (
          <div className="mt-3 inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5">
            <span className="text-sm font-black text-[#03a94d]">추천 점수 {score}점</span>
          </div>
        )}
      </div>

      <div className="flex-1 space-y-6 px-6 py-5">
        {reason && (
          <section className="rounded-xl bg-emerald-50 p-4">
            <p className="mb-1 text-xs font-black text-[#03a94d]">AI 추천 이유</p>
            <p className="text-sm leading-6 text-slate-700">{reason}</p>
            {matchedInterests && matchedInterests.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {matchedInterests.map((interest) => (
                  <Badge key={interest} className="bg-[#03c75a]">{interest}</Badge>
                ))}
              </div>
            )}
          </section>
        )}

        {showEligibilityWarning && (
          <section className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-4">
            <AlertTriangle size={18} className="mt-0.5 shrink-0 text-amber-500" />
            <p className="text-sm leading-6 text-amber-800">
              {eligibilityWarningText ?? "지원 자격이 원문에서 명확히 확인되지 않았습니다. 지원 전 원문을 꼭 확인하세요."}
            </p>
          </section>
        )}

        <section>
          <p className="mb-2 text-xs font-black text-slate-400">핵심 요약</p>
          <p className="text-sm leading-6 text-slate-700">{contest.description ?? "설명 정보가 없습니다."}</p>
        </section>

        <section>
          <p className="mb-2 text-xs font-black text-slate-400">지원 자격</p>
          <p className="text-sm leading-6 text-slate-700">
            {contest.eligibility ?? "확인 필요 — 원문 링크에서 직접 확인하세요."}
          </p>
        </section>

        <section className="grid grid-cols-2 gap-4">
          <div>
            <p className="mb-1 flex items-center gap-1.5 text-xs font-black text-slate-400">
              <Calendar size={14} /> 일정
            </p>
            <p className="text-sm font-bold text-slate-700">
              접수 시작: {contest.applicationStart ?? "확인 필요"}
            </p>
            <p className="text-sm font-bold text-slate-700">
              마감: {contest.deadline ?? "확인 필요"}
              {!contest.deadlineConfirmed && contest.deadline && (
                <span className="ml-1 text-xs font-bold text-amber-600">(미확정)</span>
              )}
            </p>
          </div>
          <div>
            <p className="mb-1 flex items-center gap-1.5 text-xs font-black text-slate-400">
              <Trophy size={14} /> 상금/혜택
            </p>
            <p className="text-sm font-bold text-slate-700">
              {contest.prizeDescription ?? (contest.prizeAmountKrw ? `${contest.prizeAmountKrw.toLocaleString()}원` : "확인 필요")}
            </p>
          </div>
          <div>
            <p className="mb-1 flex items-center gap-1.5 text-xs font-black text-slate-400">
              <MapPin size={14} /> 지역/방식
            </p>
            <p className="text-sm font-bold text-slate-700">
              {contest.region ?? "전국"} · {MODE_LABEL[contest.onlineOffline]}
            </p>
          </div>
          <div>
            <p className="mb-1 flex items-center gap-1.5 text-xs font-black text-slate-400">
              <Users size={14} /> 참가 형태
            </p>
            <p className="text-sm font-bold text-slate-700">{TEAM_LABEL[contest.individualOrTeam]}</p>
          </div>
        </section>

        {contest.submissionItems && (
          <section>
            <p className="mb-2 text-xs font-black text-slate-400">제출물</p>
            <p className="text-sm leading-6 text-slate-700">{contest.submissionItems}</p>
          </section>
        )}

        <section>
          <p className="mb-2 text-xs font-black text-slate-400">원문 링크 (출처 전체)</p>
          <div className="flex flex-col gap-1.5">
            {sourceUrls.length === 0 && <p className="text-sm text-slate-400">등록된 원문 링크가 없습니다.</p>}
            {sourceUrls.map((url) => (
              <a
                key={url}
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 text-sm font-bold text-blue-600 hover:underline"
              >
                <ExternalLink size={14} /> {url}
              </a>
            ))}
          </div>
        </section>
      </div>

      <div className="grid grid-cols-4 gap-2 border-t border-slate-100 px-6 py-4">
        {FEEDBACK_BUTTONS.map(({ type, label }) => (
          <Button
            key={type}
            size="sm"
            variant={currentFeedback === type ? "default" : "outline"}
            className={currentFeedback === type ? "bg-[#03c75a] hover:bg-[#03a94d]" : ""}
            onClick={() => onFeedback(type)}
          >
            {label}
          </Button>
        ))}
      </div>
    </div>
  );
}
