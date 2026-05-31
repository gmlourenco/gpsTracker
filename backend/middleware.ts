import { NextRequest, NextResponse } from 'next/server';

/**
 * Global API authentication middleware.
 * Protects all /api/* routes EXCEPT /api/app/version (public).
 * 
 * STRICT mode: If DEVICE_API_SECRET is not configured, returns 500.
 * This prevents accidentally running without auth in production.
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
  if (authHeader !== `Bearer ${deviceSecret}`) {
    return NextResponse.json(
      { success: false, error: 'Unauthorized' },
      { status: 401 }
    );
  }

  return NextResponse.next();
}

export const config = {
  matcher: '/api/:path*',
};
