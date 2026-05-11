function asRecord(v: unknown): Record<string, unknown> | undefined {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : undefined;
}

function stringList(v: unknown): string[] {
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
}

export type ChapterContractListProps = {
  chapters: unknown[];
};

export default function ChapterContractList({ chapters }: ChapterContractListProps) {
  if (chapters.length === 0) {
    return <p>（无章纲）</p>;
  }

  return (
    <ol style={{ paddingLeft: 20 }}>
      {chapters.map((raw, idx) => {
        const ch = asRecord(raw);
        if (!ch) return null;
        const no = typeof ch.chapterNo === "number" ? ch.chapterNo : idx + 1;
        const title = String(ch.titleHint ?? "");
        return (
          <li key={no} style={{ marginBottom: 20 }}>
            <strong>
              第 {no} 章
              {title ? ` · ${title}` : ""}
            </strong>
            <dl style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: 6, marginTop: 8 }}>
              <dt>chapter_goal</dt>
              <dd style={{ margin: 0 }}>{String(ch.chapterGoal ?? "")}</dd>
              <dt>must_cover</dt>
              <dd style={{ margin: 0 }}>
                {stringList(ch.mustCover).length === 0 ? (
                  <span>（无）</span>
                ) : (
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {stringList(ch.mustCover).map((x, i) => (
                      <li key={i}>{x}</li>
                    ))}
                  </ul>
                )}
              </dd>
              <dt>payoff</dt>
              <dd style={{ margin: 0 }}>{String(ch.payoff ?? "")}</dd>
              <dt>cliffhanger</dt>
              <dd style={{ margin: 0 }}>{String(ch.cliffhanger ?? "")}</dd>
              <dt>forbidden_moves</dt>
              <dd style={{ margin: 0 }}>
                {stringList(ch.forbiddenMoves).length === 0 ? (
                  <span>（无）</span>
                ) : (
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {stringList(ch.forbiddenMoves).map((x, i) => (
                      <li key={i}>{x}</li>
                    ))}
                  </ul>
                )}
              </dd>
            </dl>
          </li>
        );
      })}
    </ol>
  );
}
