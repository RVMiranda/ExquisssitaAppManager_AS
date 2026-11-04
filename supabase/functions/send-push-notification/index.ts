import "@supabase/functions-js/edge-runtime.d.ts";

const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const FCM_PROJECT_ID = "laexquisssita-app";
const encoder = new TextEncoder();

type PushEvent = {
  event_id: string;
  event_type: "loyalty_points" | "promotion_activated";
  cliente_id?: string;
  title: string;
  body: string;
  data?: Record<string, unknown>;
};

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
};

let cachedAccessToken: string | undefined;
let cachedAccessTokenExpiresAt = 0;

function json(data: unknown, status = 200): Response {
  return Response.json(data, { status, headers: { "Cache-Control": "no-store" } });
}

function equalSecret(left: string, right: string): boolean {
  const a = encoder.encode(left);
  const b = encoder.encode(right);
  let mismatch = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    mismatch |= (a[i] ?? 0) ^ (b[i] ?? 0);
  }
  return mismatch === 0;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

function base64UrlJson(value: unknown): string {
  return base64Url(encoder.encode(JSON.stringify(value)));
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function getSupabaseSecretKey(): string {
  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (legacy) return legacy;
  const single = Deno.env.get("SUPABASE_SECRET_KEY")?.trim();
  if (single) return single;
  const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}") as Record<string, string>;
  const key = Object.values(keys)[0]?.trim();
  if (!key) throw new Error("Missing Supabase server secret key");
  return key;
}

async function loadServiceAccount(): Promise<ServiceAccount> {
  let account: ServiceAccount;
  try {
    account = JSON.parse(requiredEnv("FCM_SERVICE_ACCOUNT")) as ServiceAccount;
  } catch {
    throw new Error("FCM_SERVICE_ACCOUNT must contain a valid service account JSON object");
  }
  if (!account.client_email || !account.private_key || !account.project_id) {
    throw new Error("FCM service account is missing client_email, private_key or project_id");
  }
  return account;
}

async function accessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedAccessToken && cachedAccessTokenExpiresAt > now + 60) return cachedAccessToken;

  const header = base64UrlJson({ alg: "RS256", typ: "JWT" });
  const claims = base64UrlJson({
    iss: account.client_email,
    scope: FCM_SCOPE,
    aud: account.token_uri || OAUTH_TOKEN_URL,
    iat: now,
    exp: now + 3600,
  });
  const unsignedJwt = `${header}.${claims}`;
  const pem = account.private_key.replace(/\\n/gu, "\n");
  const keyBytes = Uint8Array.from(atob(pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/gu, "")), (char) => char.charCodeAt(0));
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, encoder.encode(unsignedJwt)));
  const assertion = `${unsignedJwt}.${base64Url(signature)}`;
  const response = await fetch(account.token_uri || OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) {
    console.error("FCM OAuth token exchange failed", response.status);
    throw new Error("Could not obtain FCM access token");
  }
  const tokenResponse = await response.json() as { access_token?: string; expires_in?: number };
  if (!tokenResponse.access_token) throw new Error("FCM OAuth response did not contain an access token");
  cachedAccessToken = tokenResponse.access_token;
  cachedAccessTokenExpiresAt = now + (tokenResponse.expires_in ?? 3600);
  return cachedAccessToken;
}

async function supabaseRest(path: string, init: RequestInit = {}): Promise<Response> {
  const supabaseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/u, "");
  const secretKey = getSupabaseSecretKey();
  return await fetch(`${supabaseUrl}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: secretKey,
      Authorization: `Bearer ${secretKey}`,
      "Content-Type": "application/json",
      ...init.headers,
    },
  });
}

async function tokensFor(event: PushEvent): Promise<string[]> {
  const response = await supabaseRest("rpc/obtener_tokens_fcm", {
    method: "POST",
    body: JSON.stringify({ p_cliente_id: event.cliente_id ?? null }),
  });
  if (!response.ok) {
    console.error("Could not load FCM target tokens", response.status);
    throw new Error("Could not load push recipients");
  }
  const rows = await response.json() as Array<{ fcm_token: string }>;
  return [...new Set(rows.map((row) => row.fcm_token).filter(Boolean))];
}

function isUnregistered(error: unknown): boolean {
  if (!error || typeof error !== "object") return false;
  const value = error as { status?: string; details?: Array<{ errorCode?: string }> };
  return value.status === "UNREGISTERED"
    || value.details?.some((detail) => detail.errorCode === "UNREGISTERED") === true;
}

async function removeInvalidToken(token: string): Promise<void> {
  const response = await supabaseRest(`dispositivos_fcm?fcm_token=eq.${encodeURIComponent(token)}`, {
    method: "DELETE",
    headers: { Prefer: "return=minimal" },
  });
  if (!response.ok) console.error("Could not remove unregistered FCM token", response.status);
}

async function sendToToken(
  token: string,
  event: PushEvent,
  account: ServiceAccount,
): Promise<"sent" | "removed"> {
  const access = await accessToken(account);
  const data: Record<string, string> = {};
  for (const [key, value] of Object.entries(event.data ?? {})) {
    if (value !== null && value !== undefined) data[key] = String(value);
  }
  data.event_id = event.event_id;
  const projectId = account.project_id || FCM_PROJECT_ID;
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        notification: { title: event.title, body: event.body },
        data,
        android: { priority: "high", notification: { channel_id: "canal_la_exquisssita" } },
      },
    }),
  });
  if (response.ok) return "sent";
  let errorBody: { error?: { status?: string; details?: Array<{ errorCode?: string }> } } = {};
  try {
    errorBody = await response.json();
  } catch {
    // Leave the token registered when FCM does not return a parseable error.
  }
  if (isUnregistered(errorBody.error)) {
    await removeInvalidToken(token);
    return "removed";
  }
  console.error("FCM message send failed", response.status, errorBody.error?.status ?? "unknown");
  throw new Error("FCM rejected the message");
}

function validateEvent(value: unknown): value is PushEvent {
  if (!value || typeof value !== "object") return false;
  const event = value as Record<string, unknown>;
  return typeof event.event_id === "string" && event.event_id.length <= 200
    && (event.event_type === "loyalty_points" || event.event_type === "promotion_activated")
    && typeof event.title === "string" && event.title.length > 0 && event.title.length <= 120
    && typeof event.body === "string" && event.body.length <= 500
    && (event.event_type !== "loyalty_points" || typeof event.cliente_id === "string");
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const expectedSecret = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
  const receivedSecret = request.headers.get("x-push-webhook-secret") ?? "";
  if (!expectedSecret || !equalSecret(receivedSecret, expectedSecret)) {
    return json({ error: "unauthorized" }, 401);
  }

  let event: unknown;
  try {
    event = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }
  if (!validateEvent(event)) return json({ error: "invalid_event" }, 400);

  try {
    const account = await loadServiceAccount();
    const targets = await tokensFor(event);
    let sent = 0;
    let removed = 0;
    const failures: unknown[] = [];
    for (let offset = 0; offset < targets.length; offset += 20) {
      const results = await Promise.allSettled(targets.slice(offset, offset + 20)
        .map((token) => sendToToken(token, event, account)));
      for (const result of results) {
        if (result.status === "rejected") failures.push(result.reason);
        else if (result.value === "sent") sent++;
        else removed++;
      }
    }
    if (failures.length > 0) return json({ error: "partial_delivery", sent, removed }, 502);
    return json({ accepted: true, sent, removed });
  } catch (error) {
    console.error("Push dispatch failed", error instanceof Error ? error.message : "unknown error");
    return json({ error: "push_dispatch_failed" }, 502);
  }
});
