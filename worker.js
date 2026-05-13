// ══════════════════════════════════════════════
// Gemini + OpenAI + OpenRouter Proxy Worker
// Deploy করো: personalassistant.tareqrayhan1.workers.dev
// ══════════════════════════════════════════════

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

export default {
  async fetch(request, env, ctx) {

    // OPTIONS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (request.method !== "POST") {
      return new Response("Method not allowed", { status: 405, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname; // /gemini, /openai, /openrouter

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "Invalid JSON body" }, 400);
    }

    // ── /gemini ──────────────────────────────────────────
    if (path === "/gemini") {
      const { key, model, contents, system_instruction, generationConfig } = body;
      if (!key) return json({ error: "key missing" }, 400);

      const geminiBody = { contents, generationConfig: generationConfig || { maxOutputTokens: 1000 } };
      if (system_instruction) geminiBody.system_instruction = system_instruction;

      const geminiModel = model || "gemini-2.0-flash";
      const r = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${geminiModel}:generateContent?key=${key}`,
        { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(geminiBody) }
      );
      const data = await r.json();
      return json(data, r.status);
    }

    // ── /openai ──────────────────────────────────────────
    if (path === "/openai") {
      const { key, model, messages, max_tokens } = body;
      if (!key) return json({ error: "key missing" }, 400);

      const r = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": "Bearer " + key },
        body: JSON.stringify({ model: model || "gpt-4o-mini", messages, max_tokens: max_tokens || 1000 })
      });
      const data = await r.json();
      return json(data, r.status);
    }

    // ── /openrouter ──────────────────────────────────────
    if (path === "/openrouter") {
      const { key, model, messages, max_tokens } = body;
      if (!key) return json({ error: "key missing" }, 400);

      const r = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + key,
          "HTTP-Referer": "https://personalassistant.tareqrayhan1.workers.dev",
          "X-Title": "Tareq's Assistant"
        },
        body: JSON.stringify({ model: model || "openai/gpt-4o-mini", messages, max_tokens: max_tokens || 1000 })
      });
      const data = await r.json();
      return json(data, r.status);
    }

    return json({ error: "Unknown endpoint. Use /gemini, /openai, or /openrouter" }, 404);
  }
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
  });
      }
