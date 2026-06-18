'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/useAuth';
import { useToast } from '@/components/Toaster';
import { isSupabaseConfigured } from '@/lib/env';
import { Button, Card } from '@/components/ui';
import { ApiError } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const { token, ready, loginDev, loginSupabase } = useAuth();
  const { toast } = useToast();

  const [devEmail, setDevEmail] = useState('demo@merchanthub.dev');
  const [devLoading, setDevLoading] = useState(false);

  const supabaseOn = isSupabaseConfigured();
  const [sbEmail, setSbEmail] = useState('');
  const [sbPassword, setSbPassword] = useState('');
  const [sbLoading, setSbLoading] = useState(false);

  useEffect(() => {
    if (ready && token) router.replace('/dashboard');
  }, [ready, token, router]);

  async function handleDev(e: React.FormEvent) {
    e.preventDefault();
    setDevLoading(true);
    try {
      await loginDev(devEmail.trim());
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Login failed. Is the backend running?';
      toast({ title: 'Login failed', description: msg, variant: 'error' });
    } finally {
      setDevLoading(false);
    }
  }

  async function handleSupabase(e: React.FormEvent) {
    e.preventDefault();
    setSbLoading(true);
    try {
      await loginSupabase(sbEmail.trim(), sbPassword);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Supabase login failed.';
      toast({ title: 'Login failed', description: msg, variant: 'error' });
    } finally {
      setSbLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-md animate-slide-up">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-500 text-lg font-bold text-white shadow-lg shadow-indigo-500/30">
            M
          </div>
          <h1 className="text-2xl font-bold text-slate-100">MerchantHub</h1>
          <p className="text-sm text-slate-400">E-commerce analytics dashboard</p>
        </div>

        <Card className="p-6">
          <form onSubmit={handleDev} className="space-y-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-300">
                Developer login
              </label>
              <input
                type="email"
                required
                value={devEmail}
                onChange={(e) => setDevEmail(e.target.value)}
                placeholder="demo@merchanthub.dev"
                className="input"
              />
            </div>
            <Button type="submit" className="w-full" disabled={devLoading}>
              {devLoading ? 'Logging in…' : 'Log in'}
            </Button>
            <p className="text-center text-xs text-slate-500">
              The second demo tenant is{' '}
              <code className="rounded bg-white/5 px-1 py-0.5 text-slate-300">
                rival@merchanthub.dev
              </code>
            </p>
          </form>

          {supabaseOn && (
            <>
              <div className="my-5 flex items-center gap-3 text-xs text-slate-500">
                <span className="h-px flex-1 bg-white/10" />
                or sign in with Supabase
                <span className="h-px flex-1 bg-white/10" />
              </div>
              <form onSubmit={handleSupabase} className="space-y-3">
                <input
                  type="email"
                  required
                  value={sbEmail}
                  onChange={(e) => setSbEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="input"
                />
                <input
                  type="password"
                  required
                  value={sbPassword}
                  onChange={(e) => setSbPassword(e.target.value)}
                  placeholder="Password"
                  className="input"
                />
                <Button
                  type="submit"
                  variant="secondary"
                  className="w-full"
                  disabled={sbLoading}
                >
                  {sbLoading ? 'Signing in…' : 'Sign in with Supabase'}
                </Button>
              </form>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
