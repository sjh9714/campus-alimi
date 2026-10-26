"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, dday, formatDate, type Benefit, type Me } from "@/lib/api";
import { CheckIcon, ExternalIcon } from "../icons";

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
    <main id="main">
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
      {benefits === null && !error && <div className="empty">불러오는 중…</div>}
      {categories.map((c) => {
        const list = (benefits ?? []).filter((b) => b.category === c);
        return (
          <section key={c} className="block" aria-labelledby={`cat-${c}`}>
            <header className="block-head">
              <h2 id={`cat-${c}`}>
                {CATEGORY[c] ?? c} <span className="count">{list.length}</span>
              </h2>
            </header>
            <ul className="rows">
              {list.map((b) => (
                <li className="benefit-row" key={b.id}>
                  <div className="body">
                    <h3 className="title">{b.name}</h3>
                    <p className="summary">{b.summary}</p>
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
                    <p className="meta">
                      <a href={b.sourceUrl} target="_blank" rel="noreferrer">
                        출처 {b.sourceName} <ExternalIcon size={13} />
                      </a>
                      {b.stale ? (
                        <span className="badge urgent">재확인 필요</span>
                      ) : (
                        <span>확인 {formatDate(b.verifiedOn)}</span>
                      )}
                    </p>
                  </div>
                  <div className="side">
                    {b.endsOn ? (
                      <span className="ends">
                        <span className="badge warn">{ddayLabel(b.endsOn)}</span>
                        <span className="muted">{formatDate(b.endsOn)}까지</span>
                      </span>
                    ) : (
                      <span className="muted">상시</span>
                    )}
                    {me && (
                      <button className="save" onClick={() => toggle(b)} aria-pressed={b.checked}>
                        <CheckIcon />
                        {b.checked ? "받는 중" : "체크"}
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </main>
  );
}

function ddayLabel(endsOn: string): string {
  const d = dday(endsOn);
  return d < 0 ? "끝남" : d === 0 ? "오늘까지" : `D-${d}`;
}
