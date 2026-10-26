"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, dday, formatRange, type Item, type Me } from "@/lib/api";

const BUCKET_TITLE = ["내게 해당", "전체 대상", "대상 확인 필요"];

export default function Home() {
  const [items, setItems] = useState<Item[] | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [includePast, setIncludePast] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    Promise.all([api.items(includePast), api.me()])
      .then(([i, m]) => {
        setItems(i);
        setMe(m);
        setError(null);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [includePast]);

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

  const groups = [0, 1, 2].map((b) => ({ bucket: b, items: (items ?? []).filter((i) => i.bucket === b) }));
  const hasProfile = !!me && (me.entryYear !== null || me.major !== null);

  return (
    <>
      <h1>일정</h1>
      <p className="muted">
        {me ? (
          hasProfile ? (
            <>{me.grade ? `${me.grade}학년` : ""}{me.major ? ` · ${me.major}` : ""} 기준으로 정렬했어요.</>
          ) : (
            <>
              <Link href="/settings/">설정</Link>에서 입학년도와 전공을 넣으면 내 것이 위로 와요.
            </>
          )
        ) : (
          <>
            <Link href="/settings/">로그인</Link>하면 항목을 저장하고 마감 전에 알림을 받아요.
          </>
        )}
      </p>
      {error && <div className="msg error">{error}</div>}
      {items === null && !error && <p className="muted">불러오는 중...</p>}
      {items !== null && items.length === 0 && <p className="muted">표시할 일정이 없어요.</p>}
      {groups.map(
        (g) =>
          g.items.length > 0 && (
            <section key={g.bucket}>
              <h2>
                {BUCKET_TITLE[g.bucket]} · {g.items.length}
              </h2>
              {g.items.map((item) => (
                <ItemCard key={item.id} item={item} canSave={!!me} onToggle={() => toggleSave(item)} />
              ))}
            </section>
          ),
      )}
      <details onToggle={(e) => setIncludePast((e.target as HTMLDetailsElement).open)}>
        <summary>{includePast ? "지난 일정 숨기기" : "지난 일정도 보기"}</summary>
      </details>
    </>
  );
}

function ItemCard({ item, canSave, onToggle }: { item: Item; canSave: boolean; onToggle: () => void }) {
  const d = dday(item.endsOn);
  const dLabel = d < 0 ? `D+${-d}` : d === 0 ? "D-day" : `D-${d}`;
  const target =
    item.targetGrade === null || item.targetDept === null
      ? null
      : [item.targetGrade === "ALL" ? "전체 학년" : `${item.targetGrade}학년`, item.targetDept === "ALL" ? "" : item.targetDept]
          .filter(Boolean)
          .join(" · ");
  return (
    <div className="card">
      <div className="row between">
        <span className={`badge ${d >= 0 && d <= 3 ? "urgent" : ""}`}>{dLabel}</span>
        <span className="row">
          {!item.reviewed && <span className="badge">검수 전</span>}
          {item.sourceKind === "SCHEDULE" && <span className="badge accent">학사일정</span>}
          {canSave && (
            <button onClick={onToggle} aria-pressed={item.saved}>
              {item.saved ? "저장됨" : "저장"}
            </button>
          )}
        </span>
      </div>
      <p className="title">{item.title}</p>
      <p className="muted">{formatRange(item)}</p>
      {target && <p className="muted">{target}</p>}
      {item.targetNote && <p className="muted">{item.targetNote}</p>}
      {item.notice && (
        <a className="notice-link" href={item.notice.url} target="_blank" rel="noreferrer">
          [{item.notice.category}] {item.notice.title}
        </a>
      )}
    </div>
  );
}
