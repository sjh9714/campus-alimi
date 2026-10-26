import { ChevronIcon } from "./icons";

/** 월 달력. 마감이 있는 날에 점을 찍고, 날짜를 누르면 그날 마감만 보게 한다. */
export function MonthCalendar({
  month,
  onMonth,
  marked,
  selected,
  onSelect,
}: {
  month: Date;
  onMonth: (d: Date) => void;
  marked: Set<string>;
  selected: string | null;
  onSelect: (iso: string | null) => void;
}) {
  const y = month.getFullYear();
  const m = month.getMonth();
  const first = new Date(y, m, 1);
  const start = new Date(y, m, 1 - first.getDay());
  const todayIso = iso(new Date());
  const weeks: Date[][] = [];
  for (let w = 0; w < 6; w++) {
    const row: Date[] = [];
    for (let d = 0; d < 7; d++) {
      row.push(new Date(start.getFullYear(), start.getMonth(), start.getDate() + w * 7 + d));
    }
    if (w === 5 && row[0].getMonth() !== m) break;
    weeks.push(row);
  }
  return (
    <div className="calendar">
      <div className="head">
        <button className="ghost nav-btn" onClick={() => onMonth(new Date(y, m - 1, 1))} aria-label="이전 달">
          <ChevronIcon dir="left" />
        </button>
        <strong>
          {y}년 {m + 1}월
        </strong>
        <button className="ghost nav-btn" onClick={() => onMonth(new Date(y, m + 1, 1))} aria-label="다음 달">
          <ChevronIcon dir="right" />
        </button>
      </div>
      <table>
        <thead>
          <tr>
            {["일", "월", "화", "수", "목", "금", "토"].map((d) => (
              <th key={d} scope="col">
                {d}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {weeks.map((row, i) => (
            <tr key={i}>
              {row.map((d) => {
                const key = iso(d);
                const cls = [
                  "day",
                  d.getMonth() !== m ? "other" : "",
                  key === todayIso ? "today" : "",
                  key === selected ? "selected" : "",
                ]
                  .filter(Boolean)
                  .join(" ");
                return (
                  <td key={key}>
                    <button
                      className={cls}
                      aria-pressed={key === selected}
                      aria-label={`${d.getMonth() + 1}월 ${d.getDate()}일${marked.has(key) ? ", 마감 있음" : ""}`}
                      onClick={() => onSelect(key === selected ? null : key)}
                    >
                      {d.getDate()}
                      {marked.has(key) && <span className="dot" aria-hidden />}
                    </button>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function iso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
