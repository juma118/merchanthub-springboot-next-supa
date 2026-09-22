'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/useAuth';
import { useToast } from '@/components/Toaster';
import { Button, Card } from '@/components/ui';
import { ApiError } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const { token, ready, login, register } = useAuth();
  const { toast } = useToast();

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('demo@merchanthub.dev');
  const [password, setPassword] = useState('demo1234');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (ready && token) router.replace('/dashboard');
  }, [ready, token, router]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      if (mode === 'login') {
        await login(email.trim(), password);
      } else {
        await register(name.trim(), email.trim(), password);
      }
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Something went wrong. Is the backend running?';
      toast({ title: mode === 'login' ? 'Login failed' : 'Registration failed', description: msg, variant: 'error' });
    } finally {
      setLoading(false);
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
          <div className="mb-4 flex rounded-lg bg-white/5 p-1 text-sm">
            <button
              type="button"
              onClick={() => setMode('login')}
              className={`flex-1 rounded-md py-1.5 font-medium transition ${
                mode === 'login' ? 'bg-white/10 text-slate-100' : 'text-slate-400'
              }`}
            >
              Log in
            </button>
            <button
              type="button"
              onClick={() => setMode('register')}
              className={`flex-1 rounded-md py-1.5 font-medium transition ${
                mode === 'register' ? 'bg-white/10 text-slate-100' : 'text-slate-400'
              }`}
            >
              Create account
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === 'register' && (
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-300">Merchant / shop name</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Acme Outfitters"
                  className="input"
                />
              </div>
            )}
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-300">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                className="input"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-300">Password</label>
              <input
                type="password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="At least 8 characters"
                className="input"
              />
            </div>
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
            </Button>
            {mode === 'login' && (
              <p className="text-center text-xs text-slate-500">
                Demo accounts:{' '}
                <code className="rounded bg-white/5 px-1 py-0.5 text-slate-300">demo@merchanthub.dev</code>{' '}
                /{' '}
                <code className="rounded bg-white/5 px-1 py-0.5 text-slate-300">rival@merchanthub.dev</code>{' '}
                — password <code className="rounded bg-white/5 px-1 py-0.5 text-slate-300">demo1234</code> for both.
              </p>
            )}
          </form>
        </Card>
      </div>
    </div>
  );
}
