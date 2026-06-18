// Token persistence in both localStorage and a cookie so it survives reload
// and is available to any client component. SSR-safe (guards `window`).

const TOKEN_KEY = 'mh_token';
const MERCHANT_KEY = 'mh_merchant';

import type { Merchant } from './types';

function setCookie(name: string, value: string, days = 7) {
  if (typeof document === 'undefined') return;
  const expires = new Date(Date.now() + days * 864e5).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`;
}

function deleteCookie(name: string) {
  if (typeof document === 'undefined') return;
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(TOKEN_KEY, token);
  setCookie(TOKEN_KEY, token);
}

export function clearToken(): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(MERCHANT_KEY);
  deleteCookie(TOKEN_KEY);
}

export function getStoredMerchant(): Merchant | null {
  if (typeof window === 'undefined') return null;
  const raw = window.localStorage.getItem(MERCHANT_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Merchant;
  } catch {
    return null;
  }
}

export function setStoredMerchant(merchant: Merchant): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(MERCHANT_KEY, JSON.stringify(merchant));
}

export { TOKEN_KEY, MERCHANT_KEY };
