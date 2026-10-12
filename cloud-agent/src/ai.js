// Cloud AI through the Workers AI binding only. There is deliberately no other provider and no fallback:
// when the free allowance is used up the call fails and the job says so.

export class AiError extends Error {
  constructor(kind) {
    super(kind);
    this.kind = kind; // 'quota' | 'timeout' | 'empty' | 'provider'
  }
}

export const DEFAULT_MODEL = '@cf/meta/llama-3.1-8b-instruct';
const SYSTEM = 'You are a concise personal assistant. Answer in the same language as the user.';

const QUOTA_RE = /neurons|daily free allocation|free allocation|quota|rate limit|too many requests|\b(4006|429)\b/i;

export function classify(e) {
  return new AiError(QUOTA_RE.test(String(e?.message ?? e)) ? 'quota' : 'provider');
}

/** Workers AI text models answer {response} (older) or an OpenAI-style {choices:[{message:{content}}]} (newer). */
export function extractText(out) {
  const text = typeof out === 'string' ? out : out?.response ?? out?.choices?.[0]?.message?.content ?? out?.result?.response;
  return typeof text === 'string' ? text.trim() : '';
}

export async function runAi(env, prompt) {
  if (!env.AI || typeof env.AI.run !== 'function') throw new AiError('provider');
  const model = env.AI_MODEL || DEFAULT_MODEL;
  const maxTokens = Math.min(Math.max(parseInt(env.AI_MAX_TOKENS ?? '512', 10) || 512, 16), 1024);
  const timeoutMs = Math.max(parseInt(env.AI_TIMEOUT_MS ?? '20000', 10) || 20000, 100);
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new AiError('timeout')), timeoutMs);
  });
  try {
    const out = await Promise.race([
      env.AI.run(model, { messages: [{ role: 'system', content: SYSTEM }, { role: 'user', content: prompt }], max_tokens: maxTokens }),
      timeout,
    ]);
    const text = extractText(out);
    if (!text) throw new AiError('empty');
    return text;
  } catch (e) {
    throw e instanceof AiError ? e : classify(e);
  } finally {
    clearTimeout(timer);
  }
}
