import { NextResponse } from "next/server";

/** Maps JavaScript API 키는 리퍼러 제한이 적용된 키만 사용합니다. */
export function GET() {
  const apiKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!apiKey) {
    return NextResponse.json(
      { configured: false, message: "Google Maps API 키가 아직 설정되지 않았습니다." },
      { status: 503 },
    );
  }
  return NextResponse.json({ configured: true, apiKey });
}
