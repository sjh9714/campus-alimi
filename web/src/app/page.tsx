"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, dday, formatRange, type Benefit, type Card, type Feed, type Line, type Me, type Section } from "@/lib/api";
import { MonthCalendar } from "./calendar";
import { BookmarkIcon, ChevronIcon, ExternalIcon } from "./icons";

type Kind = Card["kind"];
const KIND_LABEL: Record<Kind, string> = { schedule: "학사일정", scholarship: "장학", academic: "학사" };
const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];

export default function Home() {
  const [feed, setFeed] = useState<Feed | null>(null);
  const [benefits, setBenefits] = useState<Benefit[]>([]);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [kind, setKind] = useState<Kind | "all">("all");
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [day, setDay] = useState<string | null>(null);

  const load = () =>
    Promise.all([api.feed(), api.me(), api.benefits()])
      .then(([f, m, b]) => {
        setFeed(f);
        setMe(m);
        setBenefits(b);
        setError(null);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  const toggleSave = async (line: Line) => {
    try {
      if (line.saved) await api.unsave(line.id);
      else await api.save(line.id);
      setFeed(await api.feed());
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const allCards = useMemo(() => (feed?.sections ?? []).flatMap((s) => s.cards), [feed]);
  const counts = useMemo(() => {
    const c: Record<Kind, number> = { schedule: 0, scholarship: 0, academic: 0 };
    allCards.forEach((card) => c[card.kind]++);
    return c;
  }, [allCards]);
  const marked = useMemo(
    () => new Set(allCards.flatMap((c) => [c.deadline, ...c.next].filter((l) => l.kind === "ACTION").map((l) => l.endsOn))),
    [allCards],
  );
  const saved = useMemo(
    () =>
      allCards
        .flatMap((c) => [c.deadline, ...c.next].filter((l) => l.saved).map((l) => ({ card: c, line: l })))
        .sort((a, b) => a.line.endsOn.localeCompare(b.line.endsOn)),
    [allCards],
  );
  const soonBenefits = benefits
    .filter((b) => b.endsOn && dday(b.endsOn) >= 0 && dday(b.endsOn) <= 60)
    .sort((a, b) => a.endsOn!.localeCompare(b.endsOn!));

  const filtered = (s: Section): Card[] =>
    s.cards.filter(
      (c) =>
        (kind === "all" || c.kind === kind) &&
        (!day || [c.deadline, ...c.next].some((l) => l.kind === "ACTION" && l.endsOn === day)),
    );
  const filtering = kind !== "all" || day !== null;
  const visibleSections = (feed?.sections ?? []).map((s) => ({ section: s, cards: filtered(s) })).filter((s) => s.cards.length > 0);

  return (
    <main id="main">
      <div className="page-head">
        <h1>다가오는 마감</h1>
        <p className="sub">{subtitle(me)}</p>
      </div>

      {feed === null ? (
        error ? (
          <div className="empty" role="alert">
            <p>{error}</p>
            <button onClick={load}>다시 시도</button>
          </div>
        ) : (
          <div className="empty">불러오는 중…</div>
        )
      ) : (
        <div className="home">
          <div className="feed">
            {error && (
              <div className="msg error" role="alert">
                {error}
              </div>
            )}
            <div className="toolbar">
              <div className="segmented" role="group" aria-label="종류">
                <button aria-pressed={kind === "all"} onClick={() => setKind("all")}>
                  전체 <span className="n">{allCards.length}</span>
                </button>
                {(Object.keys(KIND_LABEL) as Kind[]).map((k) => (
                  <button key={k} aria-pressed={kind === k} onClick={() => setKind(k)}>
                    {KIND_LABEL[k]} <span className="n">{counts[k]}</span>
                  </button>
                ))}
              </div>
              {day && (
                <button className="filter-pill" onClick={() => setDay(null)}>
                  {shortDate(day)} 마감만 보는 중 · 전체 보기
                </button>
              )}
            </div>

            {visibleSections.length === 0 && (
              <div className="empty">
                {filtering ? (
                  <>
                    <p>조건에 맞는 마감이 없어요.</p>
                    <button
                      onClick={() => {
                        setKind("all");
                        setDay(null);
                      }}
                    >
                      필터 지우기
                    </button>
                  </>
                ) : (
                  "지금 남은 마감이 없어요."
                )}
              </div>
            )}

            {visibleSections.map(({ section, cards }) => (
              <SectionBlock
                key={section.key}
                section={section}
                cards={cards}
                open={!section.collapsed || filtering}
                canSave={!!me}
                onToggle={toggleSave}
              />
            ))}
          </div>

          <aside className="rail" aria-label="달력과 요약">
            <div className="panel">
              <MonthCalendar month={month} onMonth={setMonth} marked={marked} selected={day} onSelect={setDay} />
            </div>
            <div className="panel">
              <h2 className="panel-title">내 저장</h2>
              {!me ? (
                <p className="muted">
                  <Link href="/settings/">로그인</Link>하면 마감을 저장해 두고 한곳에서 볼 수 있어요.
                </p>
              ) : saved.length === 0 ? (
                <p className="muted">저장한 마감이 없어요. 목록에서 저장을 눌러 보세요.</p>
              ) : (
                <ul className="side-list">
                  {saved.slice(0, 8).map(({ card, line }) => (
                    <li key={line.id}>
                      <span className="name">{line.label ? `${card.title} · ${line.label}` : card.title}</span>
                      <Dday endsOn={line.endsOn} />
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div className="panel">
              <h2 className="panel-title">
                곧 끝나는 학생 혜택
                <Link href="/benefits/" className="panel-link">
                  전체 {benefits.length}
                </Link>
              </h2>
              {soonBenefits.length === 0 ? (
                <p className="muted">60일 안에 끝나는 혜택이 없어요.</p>
              ) : (
                <ul className="side-list">
                  {soonBenefits.map((b) => (
                    <li key={b.id}>
                      <span className="name">{b.name}</span>
                      <Dday endsOn={b.endsOn!} />
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </aside>
        </div>
      )}
    </main>
  );
}

function subtitle(me: Me | null): string {
  if (!me) return "가톨릭대 학사일정과 장학·학사 공지에서 학생이 해야 할 일만 골라 마감 순으로 보여 드려요.";
  const parts = [me.grade ? `${me.grade}학년` : "", me.major ?? "", me.homeRegion ? `${me.homeRegion} 연고` : ""].filter(Boolean);
  if (parts.length === 0) return "설정에서 입학년도·전공·연고 지역을 넣으면 내게 맞는 공지가 위로 올라와요.";
  return `${parts.join(" · ")} 기준으로 내게 맞는 공지를 위로 올렸어요.`;
}

function SectionBlock({
  section,
  cards,
  open,
  canSave,
  onToggle,
}: {
  section: Section;
  cards: Card[];
  open: boolean;
  canSave: boolean;
  onToggle: (l: Line) => void;
}) {
  const rows = (
    <ul className="rows">
      {cards.map((c) => (
        <CardRow key={c.key} card={c} canSave={canSave} onToggle={onToggle} />
      ))}
    </ul>
  );
  if (section.collapsed) {
    return (
      <details className="block fold" open={open}>
        <summary>
          <span className="fold-head">
            <span className="fold-title">
              {section.title} <span className="count">{cards.length}건</span>
            </span>
            <span className="note">{section.note}</span>
          </span>
          <ChevronIcon />
        </summary>
        {rows}
      </details>
    );
  }
  const id = `section-${section.key}`;
  return (
    <section className={`block ${section.key}`} aria-labelledby={id}>
      <header className="block-head">
        <h2 id={id}>
          {section.title} <span className="count">{cards.length}</span>
        </h2>
        <p className="note">{section.note}</p>
      </header>
      {rows}
    </section>
  );
}

function CardRow({ card, canSave, onToggle }: { card: Card; canSave: boolean; onToggle: (l: Line) => void }) {
  const line = card.deadline;
  const d = dday(line.endsOn);
  const tone = d <= 3 ? "urgent" : d <= 7 ? "soon" : "";
  const date = new Date(line.endsOn + "T00:00:00");
  const audience = card.audience.scope === "GENERAL" ? null : card.audience.label;
  return (
    <li className={`row-card ${tone}`}>
      <div className="when">
        <span className="sr-only">
          마감 {date.getFullYear()}년 {date.getMonth() + 1}월 {date.getDate()}일 {WEEKDAY[date.getDay()]}요일, {ddayText(d)}
        </span>
        <span className="md" aria-hidden>
          {date.getMonth() + 1}.{date.getDate()}
        </span>
        <span className="wd" aria-hidden>
          {date.getFullYear() !== new Date().getFullYear() && `${date.getFullYear()} · `}
          {WEEKDAY[date.getDay()]}
        </span>
        <span className={`dday ${tone}`} aria-hidden>
          {ddayText(d)}
        </span>
      </div>
      <div className="body">
        <h3 className="title">
          <a href={card.url} target="_blank" rel="noreferrer" title={card.sourceTitle}>
            {card.title}
            <span className="ext">
              <ExternalIcon size={14} />
              <span className="sr-only">(학교 {card.kind === "schedule" ? "학사일정" : "공지"} 새 창)</span>
            </span>
          </a>
        </h3>
        <p className="due">
          <strong>{line.label ?? (line.startsOn ? "기간" : "마감")}</strong> {formatRange(line)}
        </p>
        <p className="meta">
          <span className={`kind ${card.kind}`}>{KIND_LABEL[card.kind]}</span>
          {audience && <span className="aud">{audience}</span>}
        </p>
        {card.audience.excerpt && <Excerpt text={card.audience.excerpt} />}
        {card.next.length > 0 && (
          <details className="next">
            <summary>
              이후 일정 {card.next.length}개 <ChevronIcon size={14} />
            </summary>
            <ul>
              {card.next.map((l) => (
                <li key={l.id} className={l.kind === "INFO" ? "info" : ""}>
                  <span className="md">{shortDate(l.endsOn)}</span>
                  <span>
                    {l.label ?? "마감"}
                    {l.kind === "INFO" && <span className="muted"> · 안내</span>}
                  </span>
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
      {canSave && (
        <button className="save" aria-pressed={line.saved} onClick={() => onToggle(line)}>
          <BookmarkIcon filled={line.saved} />
          {line.saved ? "저장됨" : "저장"}
        </button>
      )}
    </li>
  );
}

/** 지원 자격 원문 발췌. 길면 두 줄만 보이고 펼칠 수 있다. */
function Excerpt({ text }: { text: string }) {
  const [open, setOpen] = useState(false);
  const long = text.length > 70;
  return (
    <div className={`excerpt ${long && !open ? "clamped" : ""}`}>
      <span className="excerpt-label">지원 자격 · 원문 발췌</span>
      <p>{text}</p>
      {long && (
        <button className="more" aria-expanded={open} onClick={() => setOpen(!open)}>
          {open ? "접기" : "더 보기"}
        </button>
      )}
    </div>
  );
}

function Dday({ endsOn }: { endsOn: string }) {
  const d = dday(endsOn);
  return <span className={`badge ${d <= 3 ? "urgent" : d <= 7 ? "warn" : ""}`}>{ddayText(d)}</span>;
}

function ddayText(d: number): string {
  return d < 0 ? "지남" : d === 0 ? "오늘" : `D-${d}`;
}

function shortDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}.${Number(d)}`;
}
