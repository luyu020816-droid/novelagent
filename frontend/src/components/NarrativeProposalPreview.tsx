import { labelConfluenceType, labelStorylineRole, labelStorylineStatus } from "../lib/uiLabels";

type StorylineRow = {
  storylineKey?: string;
  title?: string;
  storylineRole?: string;
  status?: string;
  parentStorylineKey?: string;
  estStartChapter?: number;
  estEndChapter?: number;
  progressSummary?: string;
};

type ConfluenceRow = {
  primaryStorylineKey?: string;
  secondaryStorylineKey?: string;
  targetChapter?: number;
  confluenceType?: string;
  notes?: string;
};

type SubtextRow = {
  chapterNo?: number;
  question?: string;
  suggestedResolveChapter?: number;
};

export function NarrativeProposalPreview({ domain }: { domain: unknown }) {
  if (domain == null || typeof domain !== "object") {
    return <p className="mf-muted mf-text-sm">??????</p>;
  }
  const d = domain as {
    storylines?: StorylineRow[];
    confluences?: ConfluenceRow[];
    subtextSeeds?: SubtextRow[];
  };
  const lines = d.storylines ?? [];
  const confs = d.confluences ?? [];
  const seeds = d.subtextSeeds ?? [];

  return (
    <div style={{ fontSize: 13, lineHeight: 1.55 }}>
      <p style={{ fontWeight: 600, margin: "0 0 6px" }}>????{lines.length}?</p>
      {lines.length === 0 ? (
        <p className="mf-muted" style={{ margin: "0 0 12px" }}>
          ?
        </p>
      ) : (
        <ul style={{ margin: "0 0 12px", paddingLeft: 18 }}>
          {lines.map((s) => (
            <li key={s.storylineKey ?? s.title} style={{ marginBottom: 6 }}>
              <strong>{s.title ?? s.storylineKey}</strong>
              {" ? "}
              {labelStorylineRole(s.storylineRole)}
              {s.parentStorylineKey ? ` ? ???${s.parentStorylineKey}` : ""}
              {" ? "}
              {labelStorylineStatus(s.status)}
              {s.estStartChapter != null
                ? ` ? ?? ${s.estStartChapter}?${s.estEndChapter ?? "?"} ?`
                : ""}
              {s.progressSummary ? (
                <div className="mf-muted" style={{ fontSize: 12, marginTop: 2 }}>
                  {s.progressSummary.slice(0, 200)}
                  {s.progressSummary.length > 200 ? "?" : ""}
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      <p style={{ fontWeight: 600, margin: "0 0 6px" }}>????{confs.length}?</p>
      {confs.length === 0 ? (
        <p className="mf-muted" style={{ margin: "0 0 12px" }}>
          ?
        </p>
      ) : (
        <ul style={{ margin: "0 0 12px", paddingLeft: 18 }}>
          {confs.map((c, i) => (
            <li key={i} style={{ marginBottom: 4 }}>
              ? {c.targetChapter} ??{c.primaryStorylineKey} ? {c.secondaryStorylineKey}
              {labelConfluenceType(c.confluenceType)}
              {c.notes ? ` ? ${c.notes}` : ""}
            </li>
          ))}
        </ul>
      )}

      <p style={{ fontWeight: 600, margin: "0 0 6px" }}>?? / ????{seeds.length}?</p>
      {seeds.length === 0 ? (
        <p className="mf-muted" style={{ margin: 0 }}>
          ?
        </p>
      ) : (
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {seeds.map((s, i) => (
            <li key={i} style={{ marginBottom: 4 }}>
              ? {s.chapterNo} ?????? {s.suggestedResolveChapter ?? "?"} ????{s.question}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
