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
    <main>
      <div className="page-head">
        <h1>학생 혜택</h1>
        <p className="sub">
          대학생 자격으로 받는 것만 모았어요. 항목마다 출처와 확인한 날짜가 붙어 있어요.
          {!me && (
            <>
              {" "}
              <Link href="/settings/">로그인</Link>하면 받는 중인 혜택을 체크해 둘 수 있어요.
            </>
          )}
        </p>
      </div>
      {error && <div className="msg error" role="alert">{error}</div>}
      {benefits === null && !error && <div className="empty">불러오는 중...</div>}
      {categories.map((c, idx) => (
        <section key={c} className={idx > 0 ? "section-gap" : ""}>
          <h2>
            {CATEGORY[c] ?? c}{" "}
            <span className="count">{(benefits ?? []).filter((b) => b.category === c).length}</span>
          </h2>
          <div className="benefit-grid">
            {(benefits ?? [])
              .filter((b) => b.category === c)
              .map((b) => (
                <article className="panel benefit" key={b.id}>
                  <div className="row between">
                    <p className="title">{b.name}</p>
                    {me && (
                      <button className="save" onClick={() => toggle(b)} aria-pressed={b.checked}>
                        {b.checked ? "받는 중" : "체크"}
                      </button>
                    )}
                  </div>
                  <p className="sub">{b.summary}</p>
                  <dl>
                    {b.price && (
                      <>
                        <dt>가격</dt>
                        <dd>{b.price}</dd>
                      </>
                    )}
                    <dt>조건</dt>
                    <dd>{b.condition}</dd>
                    <dt>방법</dt>
                    <dd>{b.howTo}</dd>
                    <dt>갱신</dt>
                    <dd>{b.renewalRule}</dd>
                  </dl>
                  <div className="foot">
                    <a className="notice-link" style={{ marginTop: 0 }} href={b.sourceUrl} target="_blank" rel="noreferrer">
                      출처: {b.sourceName}
                    </a>
                    <span className="row">
                      {b.endsOn && <span className="badge warn">~{formatDate(b.endsOn)}</span>}
                      <span className={`badge ${b.stale ? "urgent" : "ok"}`}>
                        {b.stale ? "재확인 필요" : `확인 ${formatDate(b.verifiedOn)}`}
                      </span>
                    </span>
                  </div>
                </article>
              ))}
          </div>
        </section>
      ))}
    </main>
  );
}
