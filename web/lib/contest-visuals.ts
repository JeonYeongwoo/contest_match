// Decorative helpers for the contest list cards: a generated gradient banner (since real
// collected contests rarely come with usable promotional images) and a D-day calculation.

const BANNER_THEME: [string, string][] = [
  ["#03c75a", "#03a94d"],
  ["#2563eb", "#1e40af"],
  ["#f97316", "#c2410c"],
  ["#a855f7", "#7e22ce"],
  ["#06b6d4", "#0e7490"],
];

function themeFor(category: string): [string, string] {
  let hash = 0;
  for (let i = 0; i < category.length; i++) hash = (hash * 31 + category.charCodeAt(i)) >>> 0;
  return BANNER_THEME[hash % BANNER_THEME.length];
}

function wrapTitle(text: string, maxLen: number): string[] {
  const words = text.split(" ");
  const lines: string[] = [];
  let line = "";
  words.forEach((word) => {
    if ((line + " " + word).trim().length > maxLen) {
      lines.push(line.trim());
      line = word;
    } else {
      line = (line + " " + word).trim();
    }
  });
  if (line) lines.push(line);
  return lines.slice(0, 3);
}

export function contestBanner(title: string, category: string, organizer: string): string {
  const [from, to] = themeFor(category || "일반");
  const lines = wrapTitle(title, 11);
  const titleSvg = lines
    .map((line, index) => `<text x="24" y="${76 + index * 32}" font-family="Arial,sans-serif" font-size="23" font-weight="800" fill="white">${line}</text>`)
    .join("");
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="220" viewBox="0 0 400 220"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${from}"/><stop offset="1" stop-color="${to}"/></linearGradient></defs><rect width="400" height="220" fill="url(#g)"/>${titleSvg}<text x="24" y="196" font-family="Arial,sans-serif" font-size="14" font-weight="700" fill="white" opacity="0.85">${organizer}</text></svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

export function daysUntil(dateStr: string | null): number | null {
  if (!dateStr) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(dateStr);
  target.setHours(0, 0, 0, 0);
  return Math.round((target.getTime() - today.getTime()) / 86400000);
}
