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
import type { Merchant } from './types';

interface AuthContextValue {
  token: string | null;
  merchant: Merchant | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
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

  const applyToken = useCallback((tok: string, m: Merchant) => {
    persistToken(tok);
    setTokenState(tok);
    setStoredMerchant(m);
    setMerchant(m);
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

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await api.login(email.trim(), password);
      applyToken(res.token, res.merchant);
      router.push('/dashboard');
    },
    [applyToken, router],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      const res = await api.register(name.trim(), email.trim(), password);
      applyToken(res.token, res.merchant);
      router.push('/dashboard');
    },
    [applyToken, router],
  );

  const logout = useCallback(() => {
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
      login,
      register,
      logout,
      refreshMerchant,
    }),
    [token, merchant, ready, login, register, logout, refreshMerchant],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
