'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toaster';
import { Card, Spinner, EmptyState, Button } from '@/components/ui';
import type { InventoryRow } from '@/lib/types';

interface EditState {
  quantity: string;
  lowStockThreshold: string;
}

export default function InventoryPage() {
  const { toast } = useToast();
  const [rows, setRows] = useState<InventoryRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [editId, setEditId] = useState<string | null>(null);
  const [edit, setEdit] = useState<EditState>({ quantity: '', lowStockThreshold: '' });
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await api.inventory());
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to load inventory';
      toast({ title: 'Error', description: msg, variant: 'error' });
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    load();
  }, [load]);

  function startEdit(row: InventoryRow) {
    setEditId(row.productId);
    setEdit({
      quantity: String(row.quantity),
      lowStockThreshold: String(row.lowStockThreshold),
    });
  }

  async function commit(row: InventoryRow) {
    const quantity = Number(edit.quantity);
    const lowStockThreshold = Number(edit.lowStockThreshold);
    if (Number.isNaN(quantity) || Number.isNaN(lowStockThreshold)) {
      toast({ title: 'Invalid values', variant: 'error' });
      return;
    }
    setSaving(true);
    try {
      const updated = await api.updateInventory(row.productId, {
        quantity,
        lowStockThreshold,
      });
      setRows((prev) =>
        prev.map((r) => (r.productId === row.productId ? updated : r)),
      );
      setEditId(null);
      toast({ title: 'Inventory updated', variant: 'success' });
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Update failed';
      toast({ title: 'Error', description: msg, variant: 'error' });
    } finally {
      setSaving(false);
    }
  }

  const lowCount = rows.filter((r) => r.lowStock).length;

  return (
    <div className="animate-slide-up space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-100">Inventory</h1>
          <p className="text-sm text-slate-400">
            {rows.length} items · {lowCount} low stock
          </p>
        </div>
      </div>

      <Card>
        {loading ? (
          <div className="flex h-48 items-center justify-center text-slate-400">
            <Spinner />
          </div>
        ) : rows.length === 0 ? (
          <EmptyState title="No inventory" hint="Add products to track stock." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-white/5 text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="px-4 py-3 font-medium">SKU</th>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium text-right">Quantity</th>
                  <th className="px-4 py-3 font-medium text-right">Low threshold</th>
                  <th className="px-4 py-3 font-medium text-center">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {rows.map((row) => {
                  const editingThis = editId === row.productId;
                  return (
                    <tr
                      key={row.productId}
                      className={`transition-colors ${
                        row.lowStock ? 'bg-amber-500/10 hover:bg-amber-500/15' : 'hover:bg-white/5'
                      }`}
                    >
                      <td className="px-4 py-3 font-mono text-xs text-slate-500">
                        {row.sku}
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-200">{row.name}</td>
                      <td className="px-4 py-3 text-right">
                        {editingThis ? (
                          <input
                            type="number"
                            min="0"
                            value={edit.quantity}
                            onChange={(e) =>
                              setEdit({ ...edit, quantity: e.target.value })
                            }
                            className="w-20 rounded-lg border border-white/10 bg-slate-900/70 px-2 py-1 text-right text-sm text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500/40"
                          />
                        ) : (
                          <span className="text-slate-200">{row.quantity}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {editingThis ? (
                          <input
                            type="number"
                            min="0"
                            value={edit.lowStockThreshold}
                            onChange={(e) =>
                              setEdit({ ...edit, lowStockThreshold: e.target.value })
                            }
                            className="w-20 rounded-lg border border-white/10 bg-slate-900/70 px-2 py-1 text-right text-sm text-slate-100 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500/40"
                          />
                        ) : (
                          <span className="text-slate-400">{row.lowStockThreshold}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-center">
                        {row.lowStock ? (
                          <span className="inline-flex items-center rounded-full border border-amber-500/30 bg-amber-500/15 px-2 py-0.5 text-xs font-medium text-amber-300">
                            Low stock
                          </span>
                        ) : (
                          <span className="inline-flex items-center rounded-full border border-emerald-500/30 bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-300">
                            OK
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {editingThis ? (
                          <div className="flex justify-end gap-1">
                            <Button
                              size="sm"
                              disabled={saving}
                              onClick={() => commit(row)}
                            >
                              Save
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => setEditId(null)}
                            >
                              Cancel
                            </Button>
                          </div>
                        ) : (
                          <Button size="sm" variant="ghost" onClick={() => startEdit(row)}>
                            Edit
                          </Button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
