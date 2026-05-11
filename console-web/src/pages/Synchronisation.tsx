import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  Bar, BarChart, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import {
  BatchRow, LateBucket, LateSiteRow,
  fetchSyncBatches, fetchSyncDaily, fetchSyncLateSites, fetchSyncSummary,
} from '../api/client';
import { RefreshCcw } from 'lucide-react';
import { Kpi, formatInt } from '../components/Kpi';
import { PageHeader } from '../components/PageHeader';
import { GeoFilter, GeoScope } from '../components/GeoFilter';
import { SortableTh, SortState } from '../components/SortableTh';

const ENTITY_OPTIONS: { value: string; label: string }[] = [
  { value: '',                       label: 'Toutes entités' },
  { value: 'patients',               label: 'Patients' },
  { value: 'visits',                 label: 'Visites' },
  { value: 'treatment_initiations',  label: 'Initiations ARV' },
  { value: 'closures',               label: 'Clôtures' },
  { value: 'lab_results',            label: 'Examens biologiques' },
  { value: 'tpt_records',            label: 'TPT' },
  { value: 'dispensations',          label: 'Dispensations' },
];

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: '',         label: 'Tous statuts' },
  { value: 'ok',       label: 'OK' },
  { value: 'partial',  label: 'Partiel (rejets)' },
  { value: 'failed',   label: 'Échec' },
];

const BUCKETS: { value: LateBucket; label: string }[] = [
  { value: 'all',     label: 'Tous (en retard ou jamais)' },
  { value: 'late',    label: 'En retard (24h–7j)' },
  { value: 'offline', label: 'Hors ligne (> 7j)' },
  { value: 'never',   label: 'Jamais' },
];

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString('fr-FR', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '—';
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`;
  return `${Math.floor(ms / 60_000)} min ${Math.floor((ms % 60_000) / 1000)} s`;
}

function statusBadge(s: string): { label: string; tone: string } {
  switch (s) {
    case 'ok':      return { label: 'OK',      tone: 'bg-emerald-50 text-emerald-700' };
    case 'partial': return { label: 'Partiel', tone: 'bg-amber-50 text-amber-700' };
    case 'failed':  return { label: 'Échec',   tone: 'bg-rose-50 text-rose-700' };
    default:        return { label: s,         tone: 'bg-slate-100 text-slate-600' };
  }
}

function relativeAge(iso: string | null): string {
  if (!iso) return 'Jamais';
  const ageMs = Date.now() - new Date(iso).getTime();
  const ageH = ageMs / 36e5;
  if (ageH < 1) return 'À l’instant';
  if (ageH < 24) return `il y a ${Math.floor(ageH)} h`;
  return `il y a ${Math.floor(ageH / 24)} j`;
}

export function Synchronisation() {
  const [scope, setScope] = useState<GeoScope>({});
  const [tab, setTab] = useState<'batches' | 'late'>('batches');

  const summary = useQuery({
    queryKey: ['sync-summary', scope],
    queryFn: () => fetchSyncSummary(scope),
  });

  const daily = useQuery({
    queryKey: ['sync-daily', 30, scope],
    queryFn: () => fetchSyncDaily(30, scope),
  });

  return (
    <div className="px-6 py-6">
      <PageHeader
        icon={RefreshCcw}
        tone="admin"
        title="Synchronisation"
        subtitle={summary.data
          ? `${formatInt(summary.data.sitesTotal)} sites · dernier batch ${relativeAge(summary.data.lastBatchAt)}`
          : 'Chargement…'}
        right={<GeoFilter value={scope} onChange={setScope} />} />

      {/* KPIs */}
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4 mb-6">
        <Kpi label="Sites en ligne (< 24h)"
             value={summary.isError ? 'Erreur' : formatInt(summary.data?.sitesOnline)}
             hint={summary.data ? `sur ${formatInt(summary.data.sitesTotal)}` : ''}
             hintTone="positive" />
        <Kpi label="En retard (24h–7j)"
             value={summary.isError ? 'Erreur' : formatInt(summary.data?.sitesLate)}
             hint="à relancer rapidement"
             hintTone="warning" />
        <Kpi label="Hors ligne (> 7j)"
             value={summary.isError ? 'Erreur' : formatInt(summary.data?.sitesOffline)}
             hint="incluant 'jamais'"
             hintTone="warning" />
        <Kpi label="Batches reçus (24h)"
             value={summary.isError ? 'Erreur' : formatInt(summary.data?.batches24h)}
             hint={summary.data ? `${formatInt(summary.data.accepted24h)} acceptés · ${formatInt(summary.data.rejected24h)} rejetés` : ''}
             hintTone="neutral" />
      </div>

      {/* Daily volume chart */}
      <div className="card p-4 mb-6">
        <h3 className="text-sm font-medium mb-4">Batches reçus par jour (30j)</h3>
        <div className="h-48">
          {daily.isLoading ? (
            <div className="h-full flex items-center justify-center text-ink-muted text-sm">Chargement…</div>
          ) : !daily.data || daily.data.length === 0 ? (
            <div className="h-full flex items-center justify-center text-ink-muted text-sm">—</div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={daily.data} margin={{ top: 24, right: 8, left: 0, bottom: 0 }}>
                <XAxis dataKey="day" tick={{ fontSize: 10 }} stroke="#94a3b8"
                       tickFormatter={(d: string) => d.substring(8)} />
                <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" />
                <Tooltip
                  contentStyle={{ borderRadius: 6, fontSize: 12 }}
                  formatter={(v) => [formatInt(v as number), 'Batches']}
                />
                <Bar dataKey="batches" fill="#009d8e" radius={[3, 3, 0, 0]}>
                  <LabelList dataKey="batches" position="top"
                             style={{ fill: '#475569', fontSize: 10, fontWeight: 500 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-4 border-b border-slate-200">
        <button
          onClick={() => setTab('batches')}
          className={`px-3 py-2 text-sm border-b-2 transition ${
            tab === 'batches'
              ? 'border-sigdep-500 text-sigdep-700 font-medium'
              : 'border-transparent text-ink-muted hover:text-ink'
          }`}>Batches récents</button>
        <button
          onClick={() => setTab('late')}
          className={`px-3 py-2 text-sm border-b-2 transition ${
            tab === 'late'
              ? 'border-sigdep-500 text-sigdep-700 font-medium'
              : 'border-transparent text-ink-muted hover:text-ink'
          }`}>Sites en retard</button>
      </div>

      {tab === 'batches'
        ? <BatchesTable scope={scope} />
        : <LateSitesTable scope={scope} />}
    </div>
  );
}

function BatchesTable({ scope }: Readonly<{ scope: GeoScope }>) {
  const [entityType, setEntityType] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState<SortState>(null);
  const [page, setPage] = useState(0);
  const size = 50;

  const batches = useQuery({
    queryKey: ['sync-batches', scope, entityType, status, sort, page],
    queryFn: () => fetchSyncBatches({
      ...scope, entityType: entityType || undefined,
      status: status || undefined, sort, page, size,
    }),
  });

  const onSort = (s: SortState) => { setSort(s); setPage(0); };
  const totalPages = batches.data
    ? Math.max(1, Math.ceil(batches.data.total / batches.data.size)) : 1;

  return (
    <>
      <div className="flex items-center gap-3 mb-3">
        <select value={entityType} onChange={e => { setEntityType(e.target.value); setPage(0); }}
                className="rounded-md border border-slate-300 px-3 py-2 text-sm bg-white">
          {ENTITY_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}
                className="rounded-md border border-slate-300 px-3 py-2 text-sm bg-white">
          {STATUS_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <span className="text-xs text-ink-muted ml-auto">
          {batches.data ? `${formatInt(batches.data.total)} batches` : '—'}
        </span>
      </div>

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="thead-sigdep text-left">
              <tr className="text-left">
                <SortableTh k="finishedAt" sort={sort} onSort={onSort}>Reçu</SortableTh>
                <SortableTh k="site"       sort={sort} onSort={onSort}>Site</SortableTh>
                <SortableTh k="entityType" sort={sort} onSort={onSort}>Entité</SortableTh>
                <SortableTh k="received"   sort={sort} onSort={onSort} align="right">Reçus</SortableTh>
                <SortableTh k="accepted"   sort={sort} onSort={onSort} align="right">Acceptés</SortableTh>
                <SortableTh k="rejected"   sort={sort} onSort={onSort} align="right">Rejetés</SortableTh>
                <SortableTh k="durationMs" sort={sort} onSort={onSort} align="right">Durée</SortableTh>
                <SortableTh k="status"     sort={sort} onSort={onSort}>Statut</SortableTh>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {batches.isLoading ? (
                <tr><td colSpan={8} className="px-4 py-6 text-center text-ink-muted">Chargement…</td></tr>
              ) : batches.isError ? (
                <tr><td colSpan={8} className="px-4 py-6 text-center text-rose-600">Erreur de chargement</td></tr>
              ) : batches.data?.content.length === 0 ? (
                <tr><td colSpan={8} className="px-4 py-6 text-center text-ink-muted">Aucun batch</td></tr>
              ) : batches.data?.content.map(b => <BatchRowItem key={b.id} b={b} />)}
            </tbody>
          </table>
        </div>

        {batches.data && batches.data.total > size && (
          <div className="px-4 py-3 flex items-center justify-between text-sm border-t border-slate-100">
            <p className="text-ink-muted">Page {batches.data.page + 1} / {totalPages}</p>
            <div className="flex gap-2">
              <button onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={batches.data.page === 0}
                      className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
                Précédent
              </button>
              <button onClick={() => setPage(p => p + 1)}
                      disabled={batches.data.page + 1 >= totalPages}
                      className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
                Suivant
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

function BatchRowItem({ b }: Readonly<{ b: BatchRow }>) {
  const [open, setOpen] = useState(false);
  const sb = statusBadge(b.status);
  const errors = parseErrorSample(b.errorSample);
  const code = b.siteResolvedCode ?? b.siteCode ?? '—';

  return (
    <>
      <tr className="hover:bg-slate-50">
        <td className="px-4 py-2 whitespace-nowrap text-ink-muted">
          {formatTime(b.finishedAt ?? b.startedAt)}
        </td>
        <td className="px-4 py-2 text-ink-muted">
          <span className="font-mono text-xs">{code}</span>
          {b.siteName && <span className="ml-1">{b.siteName}</span>}
        </td>
        <td className="px-4 py-2 text-ink-muted">{b.entityType}</td>
        <td className="px-4 py-2 text-right tabular-nums">{formatInt(b.receivedCount)}</td>
        <td className="px-4 py-2 text-right tabular-nums text-emerald-700">{formatInt(b.accepted)}</td>
        <td className="px-4 py-2 text-right tabular-nums text-rose-600">{formatInt(b.rejected)}</td>
        <td className="px-4 py-2 text-right tabular-nums text-ink-muted">{formatDuration(b.durationMs)}</td>
        <td className="px-4 py-2">
          <button
            onClick={() => errors.length > 0 && setOpen(o => !o)}
            disabled={errors.length === 0}
            className={`text-xs px-2 py-0.5 rounded ${sb.tone} ${errors.length > 0 ? 'cursor-pointer hover:underline' : 'cursor-default'}`}>
            {sb.label}{errors.length > 0 ? ` · ${errors.length}` : ''}
          </button>
        </td>
      </tr>
      {open && errors.length > 0 && (
        <tr className="bg-amber-50/40">
          <td colSpan={8} className="px-4 py-2 text-xs">
            <div className="text-ink-muted mb-1">Échantillon d'erreurs :</div>
            <ul className="list-disc list-inside space-y-0.5">
              {errors.map((e, i) => (
                <li key={i}><span className="font-medium">{e.label}</span> <span className="text-ink-muted">×{formatInt(e.count)}</span></li>
              ))}
            </ul>
          </td>
        </tr>
      )}
    </>
  );
}

function parseErrorSample(json: string | null): { label: string; count: number }[] {
  if (!json) return [];
  try {
    const v = JSON.parse(json);
    if (Array.isArray(v)) return v as { label: string; count: number }[];
    return [];
  } catch {
    return [];
  }
}

function LateSitesTable({ scope }: Readonly<{ scope: GeoScope }>) {
  const [bucket, setBucket] = useState<LateBucket>('all');
  const [sort, setSort] = useState<SortState>(null);
  const [page, setPage] = useState(0);
  const size = 50;

  const lateSites = useQuery({
    queryKey: ['sync-late-sites', bucket, scope, sort, page],
    queryFn: () => fetchSyncLateSites({ bucket, ...scope, sort, page, size }),
  });

  const onSort = (s: SortState) => { setSort(s); setPage(0); };
  const totalPages = lateSites.data
    ? Math.max(1, Math.ceil(lateSites.data.total / lateSites.data.size)) : 1;

  return (
    <>
      <div className="flex items-center gap-3 mb-3">
        <select value={bucket} onChange={e => { setBucket(e.target.value as LateBucket); setPage(0); }}
                className="rounded-md border border-slate-300 px-3 py-2 text-sm bg-white">
          {BUCKETS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <span className="text-xs text-ink-muted ml-auto">
          {lateSites.data ? `${formatInt(lateSites.data.total)} sites` : '—'}
        </span>
      </div>

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="thead-sigdep text-left">
              <tr className="text-left">
                <SortableTh k="code"       sort={sort} onSort={onSort}>Code</SortableTh>
                <SortableTh k="name"       sort={sort} onSort={onSort}>Nom</SortableTh>
                <SortableTh k="region"     sort={sort} onSort={onSort}>Région</SortableTh>
                <SortableTh k="district"   sort={sort} onSort={onSort}>District</SortableTh>
                <SortableTh k="lastSyncAt" sort={sort} onSort={onSort}>Dernier sync</SortableTh>
                <th className="px-4 py-2 font-medium text-right">Patients</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {lateSites.isLoading ? (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-ink-muted">Chargement…</td></tr>
              ) : lateSites.isError ? (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-rose-600">Erreur de chargement</td></tr>
              ) : lateSites.data?.content.length === 0 ? (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-ink-muted">Aucun site dans ce bucket</td></tr>
              ) : lateSites.data?.content.map((s: LateSiteRow) => (
                <tr key={s.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2 font-mono text-xs">
                    <Link to={`/app/sites?q=${s.code}`} className="text-sigdep-700 hover:underline">{s.code}</Link>
                  </td>
                  <td className="px-4 py-2">{s.name}</td>
                  <td className="px-4 py-2 text-ink-muted">{s.regionName}</td>
                  <td className="px-4 py-2 text-ink-muted">{s.districtName}</td>
                  <td className="px-4 py-2 text-ink-muted">
                    {s.lastSyncAt
                      ? <><span>{formatTime(s.lastSyncAt)}</span> <span className="text-xs">({relativeAge(s.lastSyncAt)})</span></>
                      : <span className="text-rose-600">Jamais</span>}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatInt(s.patientCount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {lateSites.data && lateSites.data.total > size && (
          <div className="px-4 py-3 flex items-center justify-between text-sm border-t border-slate-100">
            <p className="text-ink-muted">Page {lateSites.data.page + 1} / {totalPages}</p>
            <div className="flex gap-2">
              <button onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={lateSites.data.page === 0}
                      className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
                Précédent
              </button>
              <button onClick={() => setPage(p => p + 1)}
                      disabled={lateSites.data.page + 1 >= totalPages}
                      className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
                Suivant
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
