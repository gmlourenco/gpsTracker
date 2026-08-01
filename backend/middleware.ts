import { proxy } from './proxy-middleware';

export function middleware(request: Parameters<typeof proxy>[0]) {
  return proxy(request);
}

export const config = {
  matcher: '/api/:path*',
};
