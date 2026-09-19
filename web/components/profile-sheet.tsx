"use client";

import { useEffect, useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import type { OnlinePreference, PrizePreference, Profile, TeamPreference } from "@/lib/api";
import { fetchProfile, saveProfile } from "@/lib/api";

const INTEREST_OPTIONS = ["AI", "Mobile", "Development", "Data", "Design", "Startup", "Hackathon", "Game"];

const EMPTY_PROFILE: Profile = {
  interests: [],
  majorOrJob: "",
  region: "",
  age: null,
  eligibilityNote: "",
  teamPreference: "ANY",
  prizePreference: "ANY",
  deadlinePreferenceDays: null,
  onlinePreference: "ANY",
};

export function ProfileSheet({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}) {
  const [profile, setProfile] = useState<Profile>(EMPTY_PROFILE);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    fetchProfile()
      .then((saved) => setProfile(saved ?? EMPTY_PROFILE))
      .finally(() => setLoading(false));
  }, [open]);

  const toggleInterest = (interest: string) => {
    setProfile((prev) => ({
      ...prev,
      interests: prev.interests.includes(interest)
        ? prev.interests.filter((item) => item !== interest)
        : [...prev.interests, interest],
    }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await saveProfile(profile);
      onSaved();
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle>내 프로필 설정</SheetTitle>
          <SheetDescription>
            관심 분야와 조건을 입력하면 맞춤 추천 정확도가 올라갑니다. 이메일/이름 등 개인정보는 수집하지 않습니다.
          </SheetDescription>
        </SheetHeader>

        {loading ? (
          <p className="px-4 text-sm text-muted-foreground">불러오는 중...</p>
        ) : (
          <div className="flex flex-col gap-5 px-4 pb-4">
            <div>
              <Label className="mb-2 block">관심 분야</Label>
              <div className="flex flex-wrap gap-2">
                {INTEREST_OPTIONS.map((interest) => (
                  <button
                    key={interest}
                    type="button"
                    onClick={() => toggleInterest(interest)}
                    className={`rounded-full px-3 py-1.5 text-xs font-bold transition ${
                      profile.interests.includes(interest)
                        ? "bg-[#03c75a] text-white"
                        : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                    }`}
                  >
                    {interest}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <Label htmlFor="majorOrJob" className="mb-2 block">전공/직업</Label>
              <Input
                id="majorOrJob"
                value={profile.majorOrJob ?? ""}
                onChange={(e) => setProfile((p) => ({ ...p, majorOrJob: e.target.value }))}
                placeholder="예: 컴퓨터공학과 재학생"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="region" className="mb-2 block">거주 지역</Label>
                <Input
                  id="region"
                  value={profile.region ?? ""}
                  onChange={(e) => setProfile((p) => ({ ...p, region: e.target.value }))}
                  placeholder="예: Seoul"
                />
              </div>
              <div>
                <Label htmlFor="age" className="mb-2 block">나이</Label>
                <Input
                  id="age"
                  type="number"
                  value={profile.age ?? ""}
                  onChange={(e) => setProfile((p) => ({ ...p, age: e.target.value ? Number(e.target.value) : null }))}
                  placeholder="예: 23"
                />
              </div>
            </div>

            <div>
              <Label htmlFor="eligibilityNote" className="mb-2 block">지원 자격 관련 메모 (선택)</Label>
              <Textarea
                id="eligibilityNote"
                value={profile.eligibilityNote ?? ""}
                onChange={(e) => setProfile((p) => ({ ...p, eligibilityNote: e.target.value }))}
                placeholder="예: 대학생, 만 34세 이하"
              />
            </div>

            <div>
              <Label className="mb-2 block">개인/팀 선호</Label>
              <Select
                value={profile.teamPreference}
                onValueChange={(value) => setProfile((p) => ({ ...p, teamPreference: value as TeamPreference }))}
              >
                <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ANY">상관없음</SelectItem>
                  <SelectItem value="INDIVIDUAL">개인</SelectItem>
                  <SelectItem value="TEAM">팀</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label className="mb-2 block">선호 상금 규모</Label>
              <Select
                value={profile.prizePreference}
                onValueChange={(value) => setProfile((p) => ({ ...p, prizePreference: value as PrizePreference }))}
              >
                <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ANY">상관없음</SelectItem>
                  <SelectItem value="LOW">100만원 미만</SelectItem>
                  <SelectItem value="MEDIUM">100만원~500만원</SelectItem>
                  <SelectItem value="HIGH">500만원 초과</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label className="mb-2 block">온라인/오프라인 선호</Label>
              <Select
                value={profile.onlinePreference}
                onValueChange={(value) => setProfile((p) => ({ ...p, onlinePreference: value as OnlinePreference }))}
              >
                <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ANY">상관없음</SelectItem>
                  <SelectItem value="ONLINE">온라인</SelectItem>
                  <SelectItem value="OFFLINE">오프라인</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label htmlFor="deadlinePreferenceDays" className="mb-2 block">
                선호 마감 여유 기간 (일)
              </Label>
              <Input
                id="deadlinePreferenceDays"
                type="number"
                value={profile.deadlinePreferenceDays ?? ""}
                onChange={(e) =>
                  setProfile((p) => ({
                    ...p,
                    deadlinePreferenceDays: e.target.value ? Number(e.target.value) : null,
                  }))
                }
                placeholder="예: 30 (기본값)"
              />
            </div>
          </div>
        )}

        <SheetFooter>
          <Button onClick={handleSave} disabled={saving || loading} className="bg-[#03c75a] hover:bg-[#03a94d]">
            {saving ? "저장 중..." : "저장하고 추천 갱신"}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
