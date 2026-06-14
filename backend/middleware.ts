import { NextRequest, NextResponse } from 'next/server';

/**
 * Global API authentication middleware.
 * Protects all /api/* routes EXCEPT /api/app/version (public).
 *
 * Security improvements:
 *   - Timing-safe comparison to prevent timing attacks
 *   - STRICT mode: rejects all requests if DEVICE_API_SECRET is not configured
 */
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

  // Timing-safe comparison to prevent timing attacks.
  // We use a constant-time string comparison approach:
  // both strings are padded to equal length before comparison.
  const expected = `Bearer ${deviceSecret}`;
  if (!timingSafeEqual(authHeader, expected)) {
    return NextResponse.json(
      { success: false, error: 'Unauthorized' },
      { status: 401 }
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
