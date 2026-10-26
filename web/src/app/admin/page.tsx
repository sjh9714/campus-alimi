"use client";

import { useEffect, useState } from "react";
import { api, formatRange, type Item, type ItemEdit, type ReviewNotice } from "@/lib/api";

const REASON: Record<ReviewNotice["reviewReason"], string> = {
  PARSED: "날짜를 읽었어요. 맞는지 확인하고 대상을 붙여 주세요.",
  IMAGE_ONLY: "본문이 그림이에요. 원문을 열어 날짜를 직접 넣어 주세요.",
  NO_DATE: "글자에서 날짜를 못 찾았어요. 마감이 없는 글이면 바로 완료.",
  PARSE_FAILED: "페이지를 못 읽었어요. 원문을 열어 확인해 주세요.",
};

export default function Admin() {
  const [queue, setQueue] = useState<ReviewNotice[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    api
      .review()
      .then((q) => {
        setQueue(q);
        setError(null);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  return (
    <main className="narrow" style={{ maxWidth: 960 }}>
      <div className="page-head">
        <h1>검수</h1>
        <p className="sub">검수 전 공지 {queue?.length ?? 0}건. 날짜가 맞는지 보고, 학년·학과를 붙이고, 완료를 누르세요.</p>
      </div>
      {error && <div className="msg error" role="alert">{error}</div>}
      {queue?.map((n) => (
        <NoticeCard key={n.id} notice={n} onChange={load} onError={setError} />
      ))}
      {queue !== null && queue.length === 0 && <p className="muted">검수할 공지가 없어요.</p>}
    </main>
  );
}

function NoticeCard({ notice, onChange, onError }: { notice: ReviewNotice; onChange: () => void; onError: (m: string) => void }) {
  const [adding, setAdding] = useState(false);
  const done = async () => {
    try {
      await api.markReviewed(notice.id);
      onChange();
    } catch (e) {
      onError((e as Error).message);
    }
  };
  return (
    <div className="panel" style={{ marginBottom: 14 }}>
      <div className="row between">
        <span className="badge accent">{notice.category}</span>
        <span className="muted">{notice.postedOn}</span>
      </div>
      <p className="title">{notice.title}</p>
      <p className="muted">{REASON[notice.reviewReason]}</p>
      <a className="notice-link" href={notice.url} target="_blank" rel="noreferrer">
        원문 열기 · {notice.articleNo}
      </a>
      {notice.attachments && <p className="muted">첨부: {notice.attachments.split("\n").join(", ")}</p>}
      {notice.items.map((item) => (
        <ItemEditor key={item.id} item={item} onChange={onChange} onError={onError} />
      ))}
      {adding ? (
        <ItemEditor
          item={null}
          noticeId={notice.id}
          onChange={() => {
            setAdding(false);
            onChange();
          }}
          onError={onError}
          onCancel={() => setAdding(false)}
        />
      ) : (
        <p className="row" style={{ marginTop: 10 }}>
          <button onClick={() => setAdding(true)}>날짜 항목 추가</button>
          <button className="primary" onClick={done}>
            검수 완료
          </button>
        </p>
      )}
    </div>
  );
}

function ItemEditor({
  item,
  noticeId,
  onChange,
  onError,
  onCancel,
}: {
  item: Item | null;
  noticeId?: number;
  onChange: () => void;
  onError: (m: string) => void;
  onCancel?: () => void;
}) {
  const [open, setOpen] = useState(item === null);
  const [edit, setEdit] = useState<ItemEdit>({
    title: item?.title ?? "",
    startsOn: item?.startsOn ?? null,
    endsOn: item?.endsOn ?? "",
    startsAt: item?.startsAt?.slice(0, 5) ?? null,
    endsAt: item?.endsAt?.slice(0, 5) ?? null,
    targetGrade: item?.targetGrade ?? null,
    targetDept: item?.targetDept ?? null,
    targetNote: item?.targetNote ?? null,
  });
  const set = (k: keyof ItemEdit, v: string) => setEdit({ ...edit, [k]: v === "" ? null : v });

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (item) await api.editItem(item.id, edit);
      else await api.addItem(noticeId!, edit);
      setOpen(false);
      onChange();
    } catch (err) {
      onError((err as Error).message);
    }
  };
  const remove = async () => {
    if (!item) return;
    try {
      await api.deleteItem(item.id);
      onChange();
    } catch (err) {
      onError((err as Error).message);
    }
  };

  if (!open && item) {
    return (
      <div className="row between" style={{ borderTop: "1px solid var(--line)", padding: "8px 0" }}>
        <span>
          {item.reviewed ? <span className="badge ok">확인됨</span> : <span className="badge">검수 전</span>} {item.title}
          <span className="muted"> · {formatRange(item)}</span>
          {item.targetGrade && (
            <span className="muted">
              {" "}
              · {item.targetGrade === "ALL" ? "전체 학년" : `${item.targetGrade}학년`}
              {item.targetDept && item.targetDept !== "ALL" ? ` · ${item.targetDept}` : ""}
            </span>
          )}
        </span>
        <button onClick={() => setOpen(true)}>고치기</button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} style={{ borderTop: "1px solid var(--line)", padding: "8px 0" }}>
      <label>제목</label>
      <input value={edit.title} onChange={(e) => set("title", e.target.value)} required />
      <div className="grid2">
        <div>
          <label>시작일 (선택)</label>
          <input type="date" value={edit.startsOn ?? ""} onChange={(e) => set("startsOn", e.target.value)} />
        </div>
        <div>
          <label>마감일</label>
          <input type="date" value={edit.endsOn} onChange={(e) => set("endsOn", e.target.value)} required />
        </div>
        <div>
          <label>시작 시각 (선택)</label>
          <input type="time" value={edit.startsAt ?? ""} onChange={(e) => set("startsAt", e.target.value)} />
        </div>
        <div>
          <label>마감 시각 (선택)</label>
          <input type="time" value={edit.endsAt ?? ""} onChange={(e) => set("endsAt", e.target.value)} />
        </div>
        <div>
          <label>대상 학년</label>
          <select value={edit.targetGrade ?? ""} onChange={(e) => set("targetGrade", e.target.value)}>
            <option value="">모름</option>
            <option value="ALL">전체</option>
            <option value="1">1학년</option>
            <option value="2">2학년</option>
            <option value="3">3학년</option>
            <option value="4">4학년</option>
          </select>
        </div>
        <div>
          <label>대상 학과 (ALL 또는 학과명)</label>
          <input value={edit.targetDept ?? ""} onChange={(e) => set("targetDept", e.target.value)} placeholder="ALL" />
        </div>
      </div>
      <label>조건 한 줄 (선택)</label>
      <input value={edit.targetNote ?? ""} onChange={(e) => set("targetNote", e.target.value)} placeholder="예: 소득 8구간 이하, 직전 학기 3.0 이상" />
      <p className="row" style={{ marginTop: 10 }}>
        <button className="primary" type="submit">
          {item ? "저장하고 확인됨으로" : "추가"}
        </button>
        {item && (
          <button type="button" onClick={remove}>
            지우기
          </button>
        )}
        <button
          type="button"
          onClick={() => {
            setOpen(false);
            onCancel?.();
          }}
        >
          닫기
        </button>
      </p>
    </form>
  );
}
