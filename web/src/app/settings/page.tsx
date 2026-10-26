"use client";

import { useEffect, useState } from "react";
import { api, type Me } from "@/lib/api";

export default function Settings() {
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [email, setEmail] = useState("");
  const [entryYear, setEntryYear] = useState("");
  const [major, setMajor] = useState("");
  const [secondMajor, setSecondMajor] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .me()
      .then((m) => {
        setMe(m);
        if (m) {
          setEntryYear(m.entryYear ? String(m.entryYear) : "");
          setMajor(m.major ?? "");
          setSecondMajor(m.secondMajor ?? "");
        }
      })
      .catch((e) => setError(e.message));
  }, []);

  const requestLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api.requestLogin(email);
      setMsg(r.message);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const updated = await api.updateProfile({
        entryYear: entryYear ? Number(entryYear) : null,
        major,
        secondMajor,
      });
      setMe(updated);
      setMsg("저장했어요.");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const logout = async () => {
    await api.logout();
    setMe(null);
    setMsg(null);
  };

  if (me === undefined && !error)
    return (
      <main className="narrow">
        <div className="empty">불러오는 중...</div>
      </main>
    );

  return (
    <main className="narrow">
      <div className="page-head">
        <h1>설정</h1>
      </div>
      <div className="panel">
      {error && <div className="msg error" role="alert">{error}</div>}
      {msg && <div className="msg">{msg}</div>}
      {!me ? (
        <form onSubmit={requestLogin}>
          <p className="muted">비밀번호는 없어요. 이메일로 온 링크를 누르면 로그인돼요.</p>
          <label htmlFor="email">이메일</label>
          <input
            id="email"
            type="email"
            inputMode="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@catholic.ac.kr"
          />
          <p style={{ marginTop: 12 }}>
            <button className="primary" type="submit" disabled={busy}>
              로그인 링크 받기
            </button>
          </p>
        </form>
      ) : (
        <>
          <p className="muted">{me.email}</p>
          <form onSubmit={saveProfile}>
            <p className="muted" style={{ marginTop: 16 }}>
              세 칸 모두 선택이에요. 넣으면 내게 해당하는 일정이 위로 와요. 소득이나 성적은 받지 않아요.
            </p>
            <label htmlFor="entryYear">입학년도</label>
            <input
              id="entryYear"
              type="number"
              inputMode="numeric"
              min={1990}
              max={new Date().getFullYear()}
              value={entryYear}
              onChange={(e) => setEntryYear(e.target.value)}
              placeholder="예: 2023"
            />
            {me.grade && entryYear && <p className="muted">지금 {me.grade}학년으로 계산돼요. 휴학했으면 다를 수 있어요.</p>}
            <label htmlFor="major">전공</label>
            <input id="major" value={major} onChange={(e) => setMajor(e.target.value)} placeholder="예: 컴퓨터정보공학부" />
            <label htmlFor="secondMajor">복수·부전공</label>
            <input id="secondMajor" value={secondMajor} onChange={(e) => setSecondMajor(e.target.value)} />
            <p style={{ marginTop: 12 }} className="row">
              <button className="primary" type="submit" disabled={busy}>
                저장
              </button>
              <button type="button" onClick={logout}>
                로그아웃
              </button>
            </p>
          </form>
          <h2>알림</h2>
          <p className="muted">새 장학 공지, 저장한 항목 마감 3일 전, 체크한 혜택 만료 7일 전. 다음 단계에서 켜져요.</p>
        </>
      )}
      </div>
    </main>
  );
}
