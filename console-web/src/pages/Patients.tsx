import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { downloadPatientsCsv, fetchPatients } from '../api/client';
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

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('fr-FR',
    { day: '2-digit', month: 'short', year: 'numeric' });
}

const SEX_LABEL: Record<string, string> = { M: 'Homme', F: 'Femme' };

export function Patients() {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const size = 25;

  const { data, isLoading, isError } = useQuery({
    queryKey: ['patients', query, page],
    queryFn: () => fetchPatients(query, page, size),
  });

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  async function handleExport() {
    setExporting(true);
    try { await downloadPatientsCsv(query); }
    catch (err) { /* eslint-disable-next-line no-console */ console.error(err); }
    finally { setExporting(false); }
  }

  return (
    <div className="px-6 py-6">
      <div className="flex items-baseline justify-between mb-4 gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Patients</h1>
          <p className="text-sm text-ink-muted">
            {data ? `${formatInt(data.total)} patients` : 'Chargement…'}
          </p>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <input
            type="search"
            value={query}
            onChange={e => { setQuery(e.target.value); setPage(0); }}
            placeholder="Rechercher (UUID, identifiant)…"
            className="w-72 rounded-md border border-slate-300 px-3 py-2 text-sm
                       focus:outline-none focus:border-sigdep-500 focus:ring-1 focus:ring-sigdep-500"
          />
          <button
            onClick={handleExport}
            disabled={exporting || !data || data.total === 0}
            className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-3 py-1.5 text-xs hover:bg-slate-50 disabled:opacity-50">
            {exporting ? 'Export…' : 'Exporter CSV'}
          </button>
        </div>
      </div>

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-ink-muted">
              <tr className="text-left">
                <th className="px-4 py-2 font-medium">Identifiant</th>
                <th className="px-4 py-2 font-medium">UPID</th>
                <th className="px-4 py-2 font-medium">Sexe</th>
                <th className="px-4 py-2 font-medium">Âge</th>
                <th className="px-4 py-2 font-medium">Date init. ARV</th>
                <th className="px-4 py-2 font-medium">Régime initial</th>
                <th className="px-4 py-2 font-medium">Dernière visite</th>
                <th className="px-4 py-2 font-medium">Dernier régime</th>
                <th className="px-4 py-2 font-medium">Site</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {isLoading ? (
                <tr><td colSpan={9} className="px-4 py-6 text-center text-ink-muted">Chargement…</td></tr>
              ) : isError ? (
                <tr><td colSpan={9} className="px-4 py-6 text-center text-rose-600">Erreur de chargement</td></tr>
              ) : data?.content.length === 0 ? (
                <tr><td colSpan={9} className="px-4 py-6 text-center text-ink-muted">Aucun patient</td></tr>
              ) : data?.content.map(p => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2">
                    <Link to={`/app/patients/${p.id}`} className="text-sigdep-700 hover:underline">
                      {p.codeArv ?? '—'}
                    </Link>
                  </td>
                  <td className="px-4 py-2 font-mono text-xs text-ink-muted">{p.upid ?? '—'}</td>
                  <td className="px-4 py-2">{p.sex ? SEX_LABEL[p.sex] ?? p.sex : '—'}</td>
                  <td className="px-4 py-2">{age(p.birthDate)}</td>
                  <td className="px-4 py-2 whitespace-nowrap text-ink-muted">{formatDate(p.arvInitDate)}</td>
                  <td className="px-4 py-2 text-ink-muted">{p.arvRegimenInitial ?? '—'}</td>
                  <td className="px-4 py-2 whitespace-nowrap text-ink-muted">{formatDate(p.lastVisitDate)}</td>
                  <td className="px-4 py-2 text-ink-muted">{p.lastArvRegimen ?? '—'}</td>
                  <td className="px-4 py-2 text-ink-muted">{p.siteName}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
