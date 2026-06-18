'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useRouter } from 'next/navigation';
import { api } from './api';
import {
  getToken,
  setToken as persistToken,
  clearToken,
  getStoredMerchant,
  setStoredMerchant,
} from './token';
import { getSupabase } from './supabase';
import { isSupabaseConfigured } from './env';
import type { Merchant } from './types';

interface AuthContextValue {
  token: string | null;
  merchant: Merchant | null;
  ready: boolean;
  loginDev: (email: string) => Promise<void>;
  loginSupabase: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshMerchant: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [token, setTokenState] = useState<string | null>(null);
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [ready, setReady] = useState(false);

  // Hydrate from storage on mount.
  useEffect(() => {
    setTokenState(getToken());
    setMerchant(getStoredMerchant());
    setReady(true);
  }, []);

  const applyToken = useCallback((tok: string, m?: Merchant) => {
    persistToken(tok);
    setTokenState(tok);
    if (m) {
      setStoredMerchant(m);
      setMerchant(m);
    }
  }, []);

  const refreshMerchant = useCallback(async () => {
    try {
      const me = await api.me();
      const m: Merchant = { id: me.id, name: me.name, email: me.email };
      setStoredMerchant(m);
      setMerchant(m);
    } catch {
      // leave existing merchant in place
    }
  }, []);

  const loginDev = useCallback(
    async (email: string) => {
      const res = await api.devToken(email);
      applyToken(res.token, res.merchant);
      router.push('/dashboard');
    },
    [applyToken, router],
  );

  const loginSupabase = useCallback(
    async (email: string, password: string) => {
      const supabase = getSupabase();
      if (!supabase) throw new Error('Supabase is not configured.');
      const { data, error } = await supabase.auth.signInWithPassword({
        email,
        password,
      });
      if (error) throw new Error(error.message);
      const accessToken = data.session?.access_token;
      if (!accessToken) throw new Error('No access token returned from Supabase.');
      applyToken(accessToken);
      try {
        const me = await api.me();
        applyToken(accessToken, { id: me.id, name: me.name, email: me.email });
      } catch {
        // backend may still resolve later
      }
      router.push('/dashboard');
    },
    [applyToken, router],
  );

  const logout = useCallback(() => {
    if (isSupabaseConfigured()) {
      const supabase = getSupabase();
      supabase?.auth.signOut().catch(() => {});
    }
    clearToken();
    setTokenState(null);
    setMerchant(null);
    router.push('/login');
  }, [router]);

  const value = useMemo(
    () => ({
      token,
      merchant,
      ready,
      loginDev,
      loginSupabase,
      logout,
      refreshMerchant,
    }),
    [token, merchant, ready, loginDev, loginSupabase, logout, refreshMerchant],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
