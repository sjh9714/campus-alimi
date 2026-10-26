"use client";

import { useEffect, useMemo, useState } from "react";
import { ApiError, api, formatRange, type Item, type ItemEdit, type ReviewNotice } from "@/lib/api";

type Reason = ReviewNotice["reviewReason"];

const REASON_LABEL: Record<Reason, string> = {
  PARSED: "날짜 읽음",
  IMAGE_ONLY: "그림만",
  NO_DATE: "날짜 없음",
  PARSE_FAILED: "읽기 실패",
};

const REASON_HELP: Record<Reason, string> = {
  PARSED: "날짜를 읽었어요. 맞는지 확인하고 대상을 붙여 주세요.",
  IMAGE_ONLY: "본문이 그림이에요. 원문을 열어 날짜를 직접 넣어 주세요.",
  NO_DATE: "글자에서 날짜를 못 찾았어요. 마감이 없는 글이면 바로 검수 완료를 누르세요.",
  PARSE_FAILED: "페이지를 못 읽었어요. 원문을 열어 확인해 주세요.",
};

const REASON_TONE: Record<Reason, string> = {
  PARSED: "ok",
  IMAGE_ONLY: "warn",
  NO_DATE: "",
  PARSE_FAILED: "urgent",
};

export default function Admin() {
  const [queue, setQueue] = useState<ReviewNotice[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [filter, setFilter] = useState<Reason | "ALL">("ALL");
  const [selected, setSelected] = useState<number | null>(null);

  const load = () =>
    api
      .review()
      .then((q) => {
        setQueue(q);
        setError(null);
        setSelected((cur) => (cur !== null && q.some((n) => n.id === cur) ? cur : null));
      })
      .catch((e) => {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) setForbidden(true);
        else setError(e.message);
      });

  useEffect(() => {
    load();
  }, []);

  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: queue?.length ?? 0 };
    (queue ?? []).forEach((n) => (c[n.reviewReason] = (c[n.reviewReason] ?? 0) + 1));
    return c;
  }, [queue]);

  const list = (queue ?? []).filter((n) => filter === "ALL" || n.reviewReason === filter);
  const current = (queue ?? []).find((n) => n.id === selected) ?? null;

  if (forbidden) {
    return (
      <main className="narrow">
        <div className="page-head">
          <h1>검수</h1>
        </div>
        <div className="empty">관리자만 들어올 수 있어요.</div>
      </main>
    );
  }

  return (
    <main>
      <div className="page-head">
        <h1>검수</h1>
        <p className="sub">
          검수 전 공지 {counts.ALL}건. 날짜가 맞는지 보고, 학년·학과를 붙이고, 검수 완료를 누르세요.
        </p>
      </div>
      {error && (
        <div className="msg error" role="alert">
          {error}
        </div>
      )}

      <div className="chips" style={{ display: "flex" }} role="group" aria-label="검수 사유 필터">
        {(["ALL", "PARSED", "IMAGE_ONLY", "NO_DATE", "PARSE_FAILED"] as const).map((r) => (
          <button key={r} className="chip" aria-pressed={filter === r} onClick={() => setFilter(r)}>
            {r === "ALL" ? "전체" : REASON_LABEL[r]} {counts[r] ?? 0}
          </button>
        ))}
      </div>

      {queue === null && !error ? (
        <div className="empty">불러오는 중...</div>
      ) : queue && queue.length === 0 ? (
        <div className="empty">검수할 공지가 없어요.</div>
      ) : (
        <div className={`review ${current ? "has-detail" : ""}`}>
          <ul className="review-list" aria-label="검수 전 공지">
            {list.map((n) => (
              <li key={n.id}>
                <button
                  className={`review-row ${n.id === selected ? "selected" : ""}`}
                  aria-current={n.id === selected}
                  onClick={() => setSelected(n.id)}
                >
                  <span className="row" style={{ gap: 6 }}>
                    <span className="badge accent">{n.category}</span>
                    <span className={`badge ${REASON_TONE[n.reviewReason]}`}>{REASON_LABEL[n.reviewReason]}</span>
                    <span className="muted" style={{ marginLeft: "auto" }}>
                      {n.postedOn}
                    </span>
                  </span>
                  <span className="review-title">{n.title}</span>
                  {n.items.length > 0 && <span className="muted">날짜 항목 {n.items.length}개</span>}
                </button>
              </li>
            ))}
            {list.length === 0 && <li className="muted" style={{ padding: 16 }}>이 사유의 공지가 없어요.</li>}
          </ul>

          <section className="review-detail" aria-label="선택한 공지">
            {current ? (
              <NoticeDetail
                key={current.id}
                notice={current}
                onBack={() => setSelected(null)}
                onChange={load}
                onError={setError}
              />
            ) : (
              <div className="empty">왼쪽에서 공지를 고르세요.</div>
            )}
          </section>
        </div>
      )}
    </main>
  );
}

function NoticeDetail({
  notice,
  onBack,
  onChange,
  onError,
}: {
  notice: ReviewNotice;
  onBack: () => void;
  onChange: () => void;
  onError: (m: string) => void;
}) {
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const done = async () => {
    setBusy(true);
    try {
      await api.markReviewed(notice.id);
      onChange();
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="panel">
      <button className="ghost back" onClick={onBack}>
        ← 목록
      </button>
      <div className="row" style={{ gap: 6 }}>
        <span className="badge accent">{notice.category}</span>
        <span className={`badge ${REASON_TONE[notice.reviewReason]}`}>{REASON_LABEL[notice.reviewReason]}</span>
        <span className="muted">{notice.postedOn} 게시</span>
      </div>
      <h2 className="detail-title">{notice.title}</h2>
      <p className="sub">{REASON_HELP[notice.reviewReason]}</p>
      <div className="row" style={{ marginTop: 10 }}>
        <a className="btn" href={notice.url} target="_blank" rel="noreferrer">
          학교 원문 열기 ↗
        </a>
        <span className="muted">글 번호 {notice.articleNo}</span>
      </div>
      {notice.attachments && (
        <p className="muted" style={{ marginTop: 10 }}>
          첨부: {notice.attachments.split("\n").join(", ")}
        </p>
      )}

      <p className="panel-title" style={{ marginTop: 22 }}>
        날짜 항목 <span className="muted">{notice.items.length}</span>
      </p>
      {notice.items.length === 0 && !adding && <p className="muted">아직 날짜 항목이 없어요.</p>}
      {notice.items.map((item) => (
        <ItemEditor key={item.id} item={item} onChange={onChange} onError={onError} />
      ))}
      {adding && (
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
      )}

      <div className="row detail-actions">
        {!adding && <button onClick={() => setAdding(true)}>+ 날짜 항목 추가</button>}
        <button className="primary" onClick={done} disabled={busy} style={{ marginLeft: "auto" }}>
          {busy ? "처리 중..." : "검수 완료"}
        </button>
      </div>
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
  const [busy, setBusy] = useState(false);
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
  const idp = `item-${item?.id ?? "new"}`;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      if (item) await api.editItem(item.id, edit);
      else await api.addItem(noticeId!, edit);
      setOpen(false);
      onChange();
    } catch (err) {
      onError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const remove = async () => {
    if (!item) return;
    setBusy(true);
    try {
      await api.deleteItem(item.id);
      onChange();
    } catch (err) {
      onError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  if (!open && item) {
    return (
      <div className="item-row">
        <div>
          <div className="row" style={{ gap: 6 }}>
            {item.reviewed ? <span className="badge ok">확인됨</span> : <span className="badge warn">검수 전</span>}
            <strong>{item.title}</strong>
          </div>
          <p className="muted" style={{ marginTop: 4 }}>
            {formatRange(item)}
            {item.targetGrade &&
              ` · ${item.targetGrade === "ALL" ? "전체 학년" : `${item.targetGrade}학년`}${
                item.targetDept && item.targetDept !== "ALL" ? ` · ${item.targetDept}` : ""
              }`}
            {!item.targetGrade && " · 대상 미정"}
          </p>
        </div>
        <button onClick={() => setOpen(true)}>고치기</button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="item-form">
      <label htmlFor={`${idp}-title`}>제목</label>
      <input id={`${idp}-title`} value={edit.title} onChange={(e) => set("title", e.target.value)} required />
      <div className="grid2">
        <div>
          <label htmlFor={`${idp}-so`}>시작일 (선택)</label>
          <input id={`${idp}-so`} type="date" value={edit.startsOn ?? ""} onChange={(e) => set("startsOn", e.target.value)} />
        </div>
        <div>
          <label htmlFor={`${idp}-eo`}>마감일</label>
          <input id={`${idp}-eo`} type="date" value={edit.endsOn} onChange={(e) => set("endsOn", e.target.value)} required />
        </div>
        <div>
          <label htmlFor={`${idp}-sa`}>시작 시각 (선택)</label>
          <input id={`${idp}-sa`} type="time" value={edit.startsAt ?? ""} onChange={(e) => set("startsAt", e.target.value)} />
        </div>
        <div>
          <label htmlFor={`${idp}-ea`}>마감 시각 (선택)</label>
          <input id={`${idp}-ea`} type="time" value={edit.endsAt ?? ""} onChange={(e) => set("endsAt", e.target.value)} />
        </div>
        <div>
          <label htmlFor={`${idp}-g`}>대상 학년</label>
          <select id={`${idp}-g`} value={edit.targetGrade ?? ""} onChange={(e) => set("targetGrade", e.target.value)}>
            <option value="">모름</option>
            <option value="ALL">전체</option>
            <option value="1">1학년</option>
            <option value="2">2학년</option>
            <option value="3">3학년</option>
            <option value="4">4학년</option>
          </select>
        </div>
        <div>
          <label htmlFor={`${idp}-d`}>대상 학과</label>
          <input
            id={`${idp}-d`}
            value={edit.targetDept ?? ""}
            onChange={(e) => set("targetDept", e.target.value)}
            placeholder="전체면 ALL"
          />
        </div>
      </div>
      <label htmlFor={`${idp}-n`}>조건 한 줄 (선택)</label>
      <input
        id={`${idp}-n`}
        value={edit.targetNote ?? ""}
        onChange={(e) => set("targetNote", e.target.value)}
        placeholder="예: 소득 8구간 이하, 직전 학기 3.0 이상"
      />
      <div className="row" style={{ marginTop: 14 }}>
        <button className="primary" type="submit" disabled={busy}>
          {item ? "저장하고 확인됨으로" : "추가"}
        </button>
        <button
          type="button"
          onClick={() => {
            setOpen(false);
            onCancel?.();
          }}
        >
          닫기
        </button>
        {item && (
          <button type="button" className="ghost danger" onClick={remove} disabled={busy} style={{ marginLeft: "auto" }}>
            항목 지우기
          </button>
        )}
      </div>
    </form>
  );
}
