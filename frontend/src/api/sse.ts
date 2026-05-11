export type SseFrameHandler = (eventName: string, dataJson: string) => void;

/**
 * POST + SSE：解析 `event:` / `data:` 帧（与 Writer / Java 透传一致）。
 */
export async function postSseStream(path: string, body: unknown, onFrame: SseFrameHandler): Promise<void> {
  const res = await fetch(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const t = await res.text();
    throw new Error(t || res.statusText);
  }
  if (!res.body) {
    throw new Error("No response body");
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let pendingEvent = "message";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let sep: number;
    while ((sep = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      let ev = pendingEvent;
      const dataParts: string[] = [];
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) {
          ev = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          dataParts.push(line.slice(5).trim());
        }
      }
      const dataStr = dataParts.join("\n");
      if (dataStr) {
        onFrame(ev, dataStr);
      }
      pendingEvent = "message";
    }
  }
}
