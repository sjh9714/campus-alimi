"use client";

import { useEffect, useState } from "react";
import { api, type Me } from "@/lib/api";

export default function Settings() {
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [entryYear, setEntryYear] = useState("");
  const [major, setMajor] = useState("");
  const [secondMajor, setSecondMajor] = useState("");
  const [saved, setSaved] = useState(false);
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
      .catch((e) => {
        setMe(null);
        setError(e.message);
      });
  }, []);

  const requestLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.requestLogin(email);
      setSentTo(email);
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
    setSaved(false);
    try {
      const updated = await api.updateProfile({
        entryYear: entryYear ? Number(entryYear) : null,
        major,
        secondMajor,
      });
      setMe(updated);
      setSaved(true);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const logout = async () => {
    await api.logout();
    setMe(null);
    setSaved(false);
  };

  return (
    <main className="narrow">
      <div className="page-head">
        <h1>설정</h1>
        <p className="sub">{me ? me.email : "로그인하면 마감을 저장하고, 내 학년·전공에 맞는 항목을 먼저 볼 수 있어요."}</p>
      </div>

      {error && (
        <div className="msg error" role="alert">
          {error}
        </div>
      )}

      {me === undefined ? (
        <div className="empty">불러오는 중...</div>
      ) : !me ? (
        <section className="panel">
          <p className="panel-title">이메일로 로그인</p>
          {sentTo ? (
            <div role="status">
              <p>
                <strong>{sentTo}</strong>로 로그인 링크를 보냈어요.
              </p>
              <p className="muted">15분 안에 메일의 링크를 누르면 로그인돼요. 한 번만 쓸 수 있어요.</p>
              <p style={{ marginTop: 14 }}>
                <button onClick={() => setSentTo(null)}>다른 이메일로 받기</button>
              </p>
            </div>
          ) : (
            <form onSubmit={requestLogin}>
              <p className="muted">비밀번호는 없어요. 이메일로 온 링크를 누르면 로그인돼요.</p>
              <label htmlFor="email">이메일</label>
              <div className="row" style={{ flexWrap: "nowrap" }}>
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
                <button className="primary" type="submit" disabled={busy} style={{ padding: "10px 16px" }}>
                  {busy ? "보내는 중..." : "링크 받기"}
                </button>
              </div>
            </form>
          )}
        </section>
      ) : (
        <>
          <section className="panel">
            <p className="panel-title">내 정보</p>
            <p className="muted">
              세 칸 모두 선택이에요. 넣으면 내게 해당하는 일정이 표시돼요. 소득이나 성적은 받지 않아요.
            </p>
            <form onSubmit={saveProfile}>
              <div className="grid2">
                <div>
                  <label htmlFor="entryYear">입학년도</label>
                  <input
                    id="entryYear"
                    type="number"
                    inputMode="numeric"
                    min={1990}
                    max={new Date().getFullYear()}
                    value={entryYear}
                    onChange={(e) => {
                      setEntryYear(e.target.value);
                      setSaved(false);
                    }}
                    placeholder="예: 2023"
                  />
                </div>
                <div>
                  <label htmlFor="grade">지금 학년 (자동 계산)</label>
                  <input id="grade" value={me.grade ? `${me.grade}학년` : "-"} readOnly disabled />
                </div>
              </div>
              <label htmlFor="major">전공</label>
              <input
                id="major"
                value={major}
                onChange={(e) => {
                  setMajor(e.target.value);
                  setSaved(false);
                }}
                placeholder="예: 컴퓨터정보공학부"
              />
              <label htmlFor="secondMajor">복수·부전공</label>
              <input
                id="secondMajor"
                value={secondMajor}
                onChange={(e) => {
                  setSecondMajor(e.target.value);
                  setSaved(false);
                }}
              />
              <p className="muted" style={{ marginTop: 8 }}>
                학년은 입학년도로 계산해요. 휴학했다면 실제와 다를 수 있어요.
              </p>
              <div className="row" style={{ marginTop: 16 }}>
                <button className="primary" type="submit" disabled={busy}>
                  {busy ? "저장 중..." : "저장"}
                </button>
                {saved && (
                  <span className="badge ok" role="status">
                    저장했어요
                  </span>
                )}
              </div>
            </form>
          </section>

          <section className="panel" style={{ marginTop: 16 }}>
            <p className="panel-title">알림</p>
            <ul className="side-list">
              <li>
                <span>새 장학 공지</span>
                <span className="badge">준비 중</span>
              </li>
              <li>
                <span>저장한 마감 3일 전</span>
                <span className="badge">준비 중</span>
              </li>
              <li>
                <span>체크한 혜택 만료 7일 전</span>
                <span className="badge">준비 중</span>
              </li>
            </ul>
          </section>

          <section className="panel" style={{ marginTop: 16 }}>
            <div className="row between">
              <span className="sub">{me.email}</span>
              <button onClick={logout}>로그아웃</button>
            </div>
          </section>
        </>
      )}
    </main>
  );
}
