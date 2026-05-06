import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchPatients } from '../api/client';
import { formatInt } from '../components/Kpi';

function age(birthDate: string | null): string {
  if (!birthDate) return '—';
  const bd = new Date(birthDate);
  const now = new Date();
  let y = now.getFullYear() - bd.getFullYear();
  const m = now.getMonth() - bd.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < bd.getDate())) y--;
  return `${y} ans`;
}

const SEX_LABEL: Record<string, string> = { M: 'Homme', F: 'Femme' };

export function Patients() {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const size = 25;

  const { data, isLoading, isError } = useQuery({
    queryKey: ['patients', query, page],
    queryFn: () => fetchPatients(query, page, size),
  });

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <div className="px-6 py-6">
      <div className="flex items-baseline justify-between mb-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Patients</h1>
          <p className="text-sm text-ink-muted">
            {data ? `${formatInt(data.total)} patients` : 'Chargement…'}
          </p>
        </div>
        <input
          type="search"
          value={query}
          onChange={e => { setQuery(e.target.value); setPage(0); }}
          placeholder="Rechercher (UUID, identifiant)…"
          className="w-72 rounded-md border border-slate-300 px-3 py-2 text-sm
                     focus:outline-none focus:border-sigdep-500 focus:ring-1 focus:ring-sigdep-500"
        />
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-ink-muted">
            <tr className="text-left">
              <th className="px-4 py-2 font-medium">Identifiant</th>
              <th className="px-4 py-2 font-medium">Sexe</th>
              <th className="px-4 py-2 font-medium">Âge</th>
              <th className="px-4 py-2 font-medium">Site</th>
              <th className="px-4 py-2 font-medium">UUID</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading ? (
              <tr><td colSpan={5} className="px-4 py-6 text-center text-ink-muted">Chargement…</td></tr>
            ) : isError ? (
              <tr><td colSpan={5} className="px-4 py-6 text-center text-rose-600">Erreur de chargement</td></tr>
            ) : data?.content.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-6 text-center text-ink-muted">Aucun patient</td></tr>
            ) : data?.content.map(p => (
              <tr key={p.id} className="hover:bg-slate-50">
                <td className="px-4 py-2">
                  <Link to={`/dashboard/patients/${p.id}`} className="text-sigdep-700 hover:underline">
                    {p.primaryIdentifier ?? '—'}
                  </Link>
                </td>
                <td className="px-4 py-2">{p.sex ? SEX_LABEL[p.sex] ?? p.sex : '—'}</td>
                <td className="px-4 py-2">{age(p.birthDate)}</td>
                <td className="px-4 py-2 text-ink-muted">{p.siteName}</td>
                <td className="px-4 py-2 font-mono text-xs text-ink-subtle">{p.sourceUuid.slice(0, 8)}…</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {data && data.total > 0 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <p className="text-ink-muted">
            Page {data.page + 1} / {totalPages}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={data.page === 0}
              className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
              Précédent
            </button>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={data.page + 1 >= totalPages}
              className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
              Suivant
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
