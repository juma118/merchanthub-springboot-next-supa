'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toaster';
import { Button, Card, Modal, Spinner, EmptyState } from '@/components/ui';
import { formatCurrency, formatNumber } from '@/lib/format';
import type { Product, ProductInput, ImportResult } from '@/lib/types';

const EMPTY_FORM: ProductInput = {
  sku: '',
  name: '',
  description: '',
  price: 0,
  imageUrl: '',
  quantity: 0,
  lowStockThreshold: 5,
};

export default function ProductsPage() {
  const { toast } = useToast();
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<{
    content: Product[];
    totalPages: number;
    totalElements: number;
  }>({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [form, setForm] = useState<ProductInput>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const [importResult, setImportResult] = useState<ImportResult | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedQ(q);
      setPage(0);
    }, 350);
    return () => clearTimeout(t);
  }, [q]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.products(debouncedQ, page, 20);
      setData({
        content: res.content,
        totalPages: res.totalPages,
        totalElements: res.totalElements,
      });
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to load products';
      toast({ title: 'Error', description: msg, variant: 'error' });
    } finally {
      setLoading(false);
    }
  }, [debouncedQ, page, toast]);

  useEffect(() => {
    load();
  }, [load]);

  function openNew() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setModalOpen(true);
  }

  function openEdit(p: Product) {
    setEditing(p);
    setForm({
      sku: p.sku,
      name: p.name,
      description: p.description ?? '',
      price: p.price,
      imageUrl: p.imageUrl ?? '',
      quantity: p.inventory?.quantity ?? 0,
      lowStockThreshold: p.inventory?.lowStockThreshold ?? 5,
    });
    setModalOpen(true);
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      if (editing) {
        await api.updateProduct(editing.id, form);
        toast({ title: 'Product updated', variant: 'success' });
      } else {
        await api.createProduct(form);
        toast({ title: 'Product created', variant: 'success' });
      }
      setModalOpen(false);
      load();
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Save failed';
      toast({ title: 'Error', description: msg, variant: 'error' });
    } finally {
      setSaving(false);
    }
  }

  async function remove(p: Product) {
    if (!window.confirm(`Delete "${p.name}"? This cannot be undone.`)) return;
    try {
      await api.deleteProduct(p.id);
      toast({ title: 'Product deleted', variant: 'success' });
      load();
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Delete failed';
      toast({ title: 'Error', description: msg, variant: 'error' });
    }
  }

  async function handleImport(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const result = await api.importProducts(file);
      setImportResult(result);
      setImportOpen(true);
      load();
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Import failed';
      toast({ title: 'Import failed', description: msg, variant: 'error' });
    } finally {
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  async function handleExport() {
    try {
      const res = await api.exportProducts();
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'products.csv';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Export failed';
      toast({ title: 'Export failed', description: msg, variant: 'error' });
    }
  }

  return (
    <div className="animate-slide-up space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-100">Products</h1>
          <p className="text-sm text-slate-400">
            {formatNumber(data.totalElements)} products
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => fileRef.current?.click()}>
            Import CSV
          </Button>
          <input
            ref={fileRef}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={handleImport}
          />
          <Button variant="secondary" size="sm" onClick={handleExport}>
            Export CSV
          </Button>
          <Button size="sm" onClick={openNew}>
            + New product
          </Button>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search by name or SKU…"
          className="input max-w-sm"
        />
      </div>

      <Card>
        {loading ? (
          <div className="flex h-48 items-center justify-center text-slate-400">
            <Spinner />
          </div>
        ) : data.content.length === 0 ? (
          <EmptyState title="No products found" hint="Create one or import a CSV." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-white/5 text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="px-4 py-3 font-medium">SKU</th>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium text-right">Price</th>
                  <th className="px-4 py-3 font-medium text-right">Qty</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {data.content.map((p) => (
                  <tr key={p.id} className="transition-colors hover:bg-white/5">
                    <td className="px-4 py-3 font-mono text-xs text-slate-500">{p.sku}</td>
                    <td className="px-4 py-3 font-medium text-slate-200">{p.name}</td>
                    <td className="px-4 py-3 text-right text-slate-300">
                      {formatCurrency(p.price)}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-300">
                      {formatNumber(p.inventory?.quantity ?? 0)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="sm" onClick={() => openEdit(p)}>
                          Edit
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => remove(p)}>
                          <span className="text-rose-400">Delete</span>
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-slate-500">
            Page {page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={page >= data.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Create / Edit modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Edit product' : 'New product'}
        wide
      >
        <form onSubmit={save} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="SKU">
              <input
                required
                value={form.sku}
                onChange={(e) => setForm({ ...form, sku: e.target.value })}
                className={inputCls}
              />
            </Field>
            <Field label="Name">
              <input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className={inputCls}
              />
            </Field>
          </div>
          <Field label="Description">
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={2}
              className={inputCls}
            />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Price">
              <input
                type="number"
                step="0.01"
                min="0"
                required
                value={form.price}
                onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
                className={inputCls}
              />
            </Field>
            <Field label="Image URL">
              <input
                value={form.imageUrl}
                onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
                className={inputCls}
              />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Quantity">
              <input
                type="number"
                min="0"
                required
                value={form.quantity}
                onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })}
                className={inputCls}
              />
            </Field>
            <Field label="Low-stock threshold">
              <input
                type="number"
                min="0"
                required
                value={form.lowStockThreshold}
                onChange={(e) =>
                  setForm({ ...form, lowStockThreshold: Number(e.target.value) })
                }
                className={inputCls}
              />
            </Field>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : editing ? 'Save changes' : 'Create product'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Import result modal */}
      <Modal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        title="Import results"
      >
        {importResult && (
          <div className="space-y-3 text-sm">
            <div className="flex gap-4">
              <div className="flex-1 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-center">
                <div className="text-2xl font-bold text-emerald-300">
                  {importResult.imported}
                </div>
                <div className="text-xs text-emerald-400">Imported</div>
              </div>
              <div className="flex-1 rounded-lg border border-indigo-500/30 bg-indigo-500/10 p-3 text-center">
                <div className="text-2xl font-bold text-indigo-300">
                  {importResult.updated}
                </div>
                <div className="text-xs text-indigo-400">Updated</div>
              </div>
              <div className="flex-1 rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-center">
                <div className="text-2xl font-bold text-rose-300">
                  {importResult.errors.length}
                </div>
                <div className="text-xs text-rose-400">Errors</div>
              </div>
            </div>
            {importResult.errors.length > 0 && (
              <div className="max-h-48 overflow-y-auto rounded-md border border-rose-500/30 bg-rose-500/10 p-3">
                <ul className="list-inside list-disc space-y-1 text-xs text-rose-300">
                  {importResult.errors.map((err, i) => (
                    <li key={i}>{err}</li>
                  ))}
                </ul>
              </div>
            )}
            <div className="flex justify-end">
              <Button onClick={() => setImportOpen(false)}>Close</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

const inputCls = 'input';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium text-slate-400">{label}</span>
      {children}
    </label>
  );
}
