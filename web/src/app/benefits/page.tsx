"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, formatDate, type Benefit, type Me } from "@/lib/api";

const CATEGORY: Record<string, string> = {
  TOOL: "개발·학습 도구",
  SUBSCRIPTION: "구독",
  SCHOLARSHIP: "장학·학비",
  SCHOOL: "학교",
  TRANSPORT: "교통",
  FINANCE: "금융",
  CULTURE: "문화",
};

export default function Benefits() {
  const [benefits, setBenefits] = useState<Benefit[] | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    Promise.all([api.benefits(), api.me()])
      .then(([b, m]) => {
        setBenefits(b);
        setMe(m);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  const toggle = async (b: Benefit) => {
    if (!me) return;
    try {
      if (b.checked) await api.uncheck(b.id);
      else await api.check(b.id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const categories = Array.from(new Set((benefits ?? []).map((b) => b.category)));
  return (
    <>
      <h1>혜택</h1>
      <p className="muted">
        대학생 자격으로 받는 것만 있어요. 항목마다 출처와 확인한 날짜가 붙어 있어요.
        {!me && (
          <>
            {" "}
            <Link href="/settings/">로그인</Link>하면 받은 것을 체크해 둘 수 있어요.
          </>
        )}
      </p>
      {error && <div className="msg error">{error}</div>}
      {benefits === null && !error && <p className="muted">불러오는 중...</p>}
      {categories.map((c) => (
        <section key={c}>
          <h2>{CATEGORY[c] ?? c}</h2>
          {(benefits ?? [])
            .filter((b) => b.category === c)
            .map((b) => (
              <div className="card" key={b.id}>
                <div className="row between">
                  <p className="title">{b.name}</p>
                  {me && (
                    <button onClick={() => toggle(b)} aria-pressed={b.checked}>
                      {b.checked ? "받는 중" : "체크"}
                    </button>
                  )}
                </div>
                <p>{b.summary}</p>
                {b.price && <p className="muted">가격 · {b.price}</p>}
                <p className="muted">조건 · {b.condition}</p>
                <p className="muted">방법 · {b.howTo}</p>
                <p className="muted">갱신 · {b.renewalRule}</p>
                <div className="row">
                  <a className="notice-link" href={b.sourceUrl} target="_blank" rel="noreferrer">
                    출처: {b.sourceName}
                  </a>
                  <span className={`badge ${b.stale ? "urgent" : "ok"}`}>
                    {b.stale ? "재확인 필요" : `확인 ${formatDate(b.verifiedOn)}`}
                  </span>
                  {b.endsOn && <span className="badge">~{formatDate(b.endsOn)}</span>}
                </div>
              </div>
            ))}
        </section>
      ))}
    </>
  );
}
