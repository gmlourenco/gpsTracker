import { NextRequest, NextResponse } from 'next/server';

/**
 * Global API authentication middleware.
 * Protects all /api/* routes EXCEPT /api/app/version (public).
 *
 * Security improvements:
 *   - Timing-safe comparison to prevent timing attacks
 *   - STRICT mode: rejects all requests if DEVICE_API_SECRET is not configured
 *   - In-memory sliding window rate limiting (H-3)
 */

// ── Rate Limiting ───────────────────────────────────────────────────────────
const RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute
const RATE_LIMIT_MAX_REQUESTS = 60;  // 60 requests per minute per identifier
const RATE_LIMIT_CLEANUP_INTERVAL_MS = 5 * 60_000; // Clean stale entries every 5 min

interface RateLimitEntry {
  count: number;
  resetAt: number;
}

const rateLimitMap = new Map<string, RateLimitEntry>();
let lastCleanup = Date.now();

/**
 * Returns true if the request is within rate limits, false if exceeded.
 * Uses a simple fixed-window counter per identifier.
 */
function checkRateLimit(identifier: string): boolean {
  const now = Date.now();

  // Periodically purge expired entries to prevent memory leaks
  if (now - lastCleanup > RATE_LIMIT_CLEANUP_INTERVAL_MS) {
    lastCleanup = now;
    for (const [key, entry] of rateLimitMap) {
      if (now > entry.resetAt) rateLimitMap.delete(key);
    }
  }

  const entry = rateLimitMap.get(identifier);
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(identifier, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS });
    return true;
  }
  entry.count++;
  return entry.count <= RATE_LIMIT_MAX_REQUESTS;
}

// ── Middleware ───────────────────────────────────────────────────────────────

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Allow public endpoints without auth
  if (pathname.startsWith('/api/app/version')) {
    return NextResponse.next();
  }

  const deviceSecret = process.env.DEVICE_API_SECRET;

  // STRICT: Server must have auth configured
  if (!deviceSecret) {
    console.error('[middleware] DEVICE_API_SECRET is not configured — rejecting all API requests');
    return NextResponse.json(
      { success: false, error: 'Server misconfiguration: authentication not configured' },
      { status: 500 }
    );
  }

  const authHeader = request.headers.get('authorization');
  if (!authHeader) {
    return NextResponse.json(
      { success: false, error: 'Unauthorized' },
      { status: 401 }
    );
  }

  const isUserRoute = pathname.startsWith('/api/auth') || pathname.startsWith('/api/positions');
  const expected = `Bearer ${deviceSecret}`;
  const isSecretAuth = timingSafeEqual(authHeader, expected);

  if (!isSecretAuth) {
    if (isUserRoute && authHeader.startsWith('Bearer eyJ')) {
      // Let the route handler verify the Supabase JWT
    } else {
      return NextResponse.json(
        { success: false, error: 'Unauthorized' },
        { status: 401 }
      );
    }
  }

  // ── Rate limiting (H-3) ────────────────────────────────────────────────
  // Identify by device serial header, falling back to IP address
  const identifier =
    request.headers.get('x-device-serial') ||
    request.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    'unknown';

  if (!checkRateLimit(identifier)) {
    return NextResponse.json(
      { success: false, error: 'Too many requests. Please slow down.' },
      { status: 429 }
    );
  }

  return NextResponse.next();
}

/**
 * Constant-time string comparison.
 * Uses XOR-based comparison to prevent timing side-channel attacks.
 * Both strings are compared byte-by-byte regardless of where they differ.
 */
function timingSafeEqual(a: string, b: string): boolean {
  // Length check is intentionally NOT an early return — we still do
  // the full XOR loop to avoid leaking length information via timing.
  const maxLen = Math.max(a.length, b.length);
  let mismatch = a.length !== b.length ? 1 : 0;

  for (let i = 0; i < maxLen; i++) {
    const ca = i < a.length ? a.charCodeAt(i) : 0;
    const cb = i < b.length ? b.charCodeAt(i) : 0;
    mismatch |= ca ^ cb;
  }

  return mismatch === 0;
}

export const config = {
  matcher: '/api/:path*',
};

