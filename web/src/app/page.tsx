"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, dday, formatDate, formatRange, type Benefit, type Item, type Me } from "@/lib/api";
import { MonthCalendar } from "./calendar";

type Kind = "schedule" | "scholarship" | "academic";

const KIND_LABEL: Record<Kind, string> = {
  schedule: "학사일정",
  scholarship: "장학",
  academic: "학사공지",
};

function kindOf(item: Item): Kind {
  if (item.sourceKind === "SCHEDULE") return "schedule";
  return item.notice?.category === "장학" ? "scholarship" : "academic";
}

/** 마감까지 남은 날로 묶는다. 이번 주 = 7일 안, 이번 달 = 30일 안. */
function groupOf(d: number): "week" | "month" | "later" {
  if (d <= 7) return "week";
  if (d <= 30) return "month";
  return "later";
}

const GROUP_LABEL = { week: "이번 주 마감", month: "한 달 안", later: "그 뒤" } as const;

export default function Home() {
  const [items, setItems] = useState<Item[] | null>(null);
  const [benefits, setBenefits] = useState<Benefit[]>([]);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [kinds, setKinds] = useState<Set<Kind>>(new Set(["schedule", "scholarship", "academic"]));
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [day, setDay] = useState<string | null>(null);

  const load = () =>
    Promise.all([api.items(false), api.me(), api.benefits()])
      .then(([i, m, b]) => {
        setItems(i);
        setMe(m);
        setBenefits(b);
        setError(null);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  const toggleKind = (k: Kind) => {
    const next = new Set(kinds);
    if (next.has(k)) next.delete(k);
    else next.add(k);
    setKinds(next);
  };

  const toggleSave = async (item: Item) => {
    if (!me) return;
    try {
      if (item.saved) await api.unsave(item.id);
      else await api.save(item.id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const counts = useMemo(() => {
    const c: Record<Kind, number> = { schedule: 0, scholarship: 0, academic: 0 };
    (items ?? []).forEach((i) => c[kindOf(i)]++);
    return c;
  }, [items]);

  const visible = useMemo(
    () =>
      (items ?? [])
        .filter((i) => kinds.has(kindOf(i)))
        .filter((i) => !day || i.endsOn === day)
        .sort((a, b) => a.endsOn.localeCompare(b.endsOn) || a.bucket - b.bucket),
    [items, kinds, day],
  );

  const marked = useMemo(
    () => new Set((items ?? []).filter((i) => kinds.has(kindOf(i))).map((i) => i.endsOn)),
    [items, kinds],
  );

  const groups = (["week", "month", "later"] as const)
    .map((g) => ({ g, items: visible.filter((i) => groupOf(dday(i.endsOn)) === g) }))
    .filter((x) => x.items.length > 0);

  const saved = (items ?? []).filter((i) => i.saved).sort((a, b) => a.endsOn.localeCompare(b.endsOn));
  const weekCount = (items ?? []).filter((i) => dday(i.endsOn) <= 7).length;

  return (
    <main>
      <div className="page-head">
        <h1>다가오는 마감</h1>
        <p className="sub">
          {me
            ? me.grade || me.major
              ? `${me.grade ? `${me.grade}학년` : ""}${me.major ? ` · ${me.major}` : ""} 기준으로 내게 해당하는 항목을 표시했어요.`
              : "설정에서 입학년도와 전공을 넣으면 내게 해당하는 항목이 표시돼요."
            : "가톨릭대 학사일정과 장학·학사 공지에서 날짜만 모았어요. 누르면 학교 원문으로 갑니다."}
        </p>
      </div>

      {error && <div className="msg error" role="alert">{error}</div>}

      <div className="chips" role="group" aria-label="종류 필터">
        {(Object.keys(KIND_LABEL) as Kind[]).map((k) => (
          <button key={k} className="chip" aria-pressed={kinds.has(k)} onClick={() => toggleKind(k)}>
            {KIND_LABEL[k]} {counts[k]}
          </button>
        ))}
      </div>

      <div className="dashboard">
        <aside className="left">
          <div className="panel">
            <MonthCalendar month={month} onMonth={setMonth} marked={marked} selected={day} onSelect={setDay} />
          </div>
          <div className="panel">
            <p className="panel-title">종류</p>
            <div className="filters">
              {(Object.keys(KIND_LABEL) as Kind[]).map((k) => (
                <label key={k} className="filter">
                  <span className="label">
                    <input type="checkbox" checked={kinds.has(k)} onChange={() => toggleKind(k)} />
                    {KIND_LABEL[k]}
                  </span>
                  <span className="n">{counts[k]}</span>
                </label>
              ))}
            </div>
          </div>
        </aside>

        <section aria-label="마감 목록">
          {day && (
            <div className="msg row between">
              <span>{formatDate(day)} 마감만 보는 중</span>
              <button className="ghost" onClick={() => setDay(null)}>
                전체 보기
              </button>
            </div>
          )}
          {items === null && !error && <div className="empty">불러오는 중...</div>}
          {items !== null && visible.length === 0 && (
            <div className="empty">{day ? "이날 마감인 항목이 없어요." : "표시할 마감이 없어요."}</div>
          )}
          {groups.map(({ g, items: list }) => (
            <div className="timeline-group" key={g}>
              <h2>
                {GROUP_LABEL[g]} <span className="count">{list.length}</span>
              </h2>
              {list.map((item) => (
                <ItemCard key={item.id} item={item} canSave={!!me} onToggle={() => toggleSave(item)} />
              ))}
            </div>
          ))}
        </section>

        <aside className="right">
          <div className="panel">
            <p className="panel-title">이번 주 마감</p>
            <div className="stat">
              <span className="big">{weekCount}</span>
              <span className="muted">건</span>
            </div>
          </div>
          <div className="panel">
            <p className="panel-title">내 저장</p>
            {!me ? (
              <p className="muted">
                <Link href="/settings/">로그인</Link>하면 마감을 저장하고 3일 전에 알림을 받아요.
              </p>
            ) : saved.length === 0 ? (
              <p className="muted">저장한 항목이 없어요. 목록에서 저장을 눌러 보세요.</p>
            ) : (
              <ul className="side-list">
                {saved.slice(0, 6).map((i) => (
                  <li key={i.id}>
                    <span className="name">{i.title}</span>
                    <DdayBadge endsOn={i.endsOn} />
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="panel">
            <p className="panel-title">
              학생 혜택 <Link href="/benefits/">전체 {benefits.length} →</Link>
            </p>
            <ul className="side-list">
              {benefits.slice(0, 5).map((b) => (
                <li key={b.id}>
                  <span className="name">{b.name}</span>
                  {b.endsOn ? <DdayBadge endsOn={b.endsOn} /> : <span className="badge ok">상시</span>}
                </li>
              ))}
            </ul>
          </div>
        </aside>
      </div>
    </main>
  );
}

function DdayBadge({ endsOn }: { endsOn: string }) {
  const d = dday(endsOn);
  const label = d < 0 ? "지남" : d === 0 ? "오늘" : `D-${d}`;
  return <span className={`badge ${d <= 3 ? "urgent" : d <= 7 ? "warn" : ""}`}>{label}</span>;
}

/** "[학생지원팀] 2026년 ○○ 장학생 선발 안내(10. 12.(월) ~ 10. 28.(수))" → "2026년 ○○ 장학생 선발 안내" */
function shortNoticeTitle(t: string): string {
  return t
    .replace(/^\s*(\[[^\]]*\]\s*)+/, "")
    .replace(/\s*\([^()]*(\([^()]*\)[^()]*)*\)\s*$/, (m) => (/\d{1,2}\s*[./월]\s*\d{1,2}/.test(m) ? "" : m))
    .trim();
}

/**
 * 공지 표에서 뽑은 항목은 제목이 "트리니티 신청"처럼 칸 이름뿐이라 무슨 일인지 모른다.
 * 그럴 때는 공지 제목을 크게, 칸 이름을 작게 보여준다.
 */
function headingOf(item: Item): { heading: string; step: string | null } {
  if (!item.notice) return { heading: item.title, step: null };
  const notice = shortNoticeTitle(item.notice.title);
  if (notice.includes(item.title) || item.title.includes(notice)) return { heading: item.title, step: null };
  return { heading: notice, step: item.title };
}

function ItemCard({ item, canSave, onToggle }: { item: Item; canSave: boolean; onToggle: () => void }) {
  const { heading, step } = headingOf(item);
  const d = dday(item.endsOn);
  const tone = d < 0 ? "past" : d <= 3 ? "urgent" : d <= 7 ? "soon" : "";
  const dLabel = d < 0 ? "지남" : d === 0 ? "오늘" : `D-${d}`;
  const [, mm, dd] = item.endsOn.split("-");
  const target =
    item.targetGrade === null || item.targetDept === null
      ? null
      : [item.targetGrade === "ALL" ? "전체 학년" : `${item.targetGrade}학년`, item.targetDept === "ALL" ? "" : item.targetDept]
          .filter(Boolean)
          .join(" · ");
  return (
    <article className={`item ${item.bucket === 0 ? "mine" : ""}`}>
      <div className={`dday ${tone}`} aria-label={`마감 ${dLabel}`}>
        <span className="num">{dLabel}</span>
        <span className="date">
          {Number(mm)}.{Number(dd)}
        </span>
      </div>
      <div>
        <p className="title">{heading}</p>
        <p className="sub">
          {step && <strong className="step">{step}</strong>}
          {formatRange(item)}
        </p>
        <div className="meta">
          <span className="badge accent">{KIND_LABEL[kindOf(item)]}</span>
          {item.bucket === 0 && <span className="badge ok">내게 해당</span>}
          {target && item.bucket !== 0 && <span className="badge">{target}</span>}
          {item.bucket === 2 && <span className="badge">대상 확인 필요</span>}
          {!item.reviewed && <span className="badge warn">검수 전</span>}
        </div>
        {item.targetNote && <p className="muted" style={{ marginTop: 6 }}>{item.targetNote}</p>}
        {item.notice && (
          <a className="notice-link" href={item.notice.url} target="_blank" rel="noreferrer">
            학교 공지 원문 보기 ↗
          </a>
        )}
      </div>
      {canSave && (
        <div className="actions">
          <button className="save" onClick={onToggle} aria-pressed={item.saved}>
            {item.saved ? "저장됨" : "저장"}
          </button>
        </div>
      )}
    </article>
  );
}
