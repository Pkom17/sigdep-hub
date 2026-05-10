import { useQuery } from '@tanstack/react-query';
import {
  Bar, BarChart, ResponsiveContainer, XAxis, YAxis, Tooltip,
} from 'recharts';
import { fetchDashboardKpis } from '../api/client';
import { Kpi, formatInt, formatPercent } from '../components/Kpi';
import { PageHeader } from '../components/PageHeader';

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
}

function AlertRow({ label, badge, tone }: {
  label: string; badge: string; tone: 'critical' | 'warning' | 'info' | 'ok';
}) {
  const tones = {
    critical: 'bg-rose-50 text-rose-700',
    warning:  'bg-amber-50 text-amber-700',
    info:     'bg-slate-100 text-slate-600',
    ok:       'bg-emerald-50 text-emerald-700',
  };
  return (
    <div className="flex items-center justify-between py-2 text-sm">
      <span className="text-ink">{label}</span>
      <span className={`text-xs px-2 py-0.5 rounded ${tones[tone]}`}>{badge}</span>
    </div>
  );
}

export function Dashboard() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboardKpis'],
    queryFn: fetchDashboardKpis,
  });

  const periodLabel = new Date().toLocaleDateString('fr-FR',
    { month: 'long', year: 'numeric' });

  return (
    <div className="px-6 py-6">
      <PageHeader
        title="Vue d’ensemble"
        subtitle={`Périmètre : National · ${periodLabel.charAt(0).toUpperCase() + periodLabel.slice(1)}`} />

      {/* KPI row */}
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4 mb-6">
        <Kpi label="File active"
             value={isError ? 'Erreur' : formatInt(data?.fileActive)}
             hint="12 mois glissants"
             hintTone="neutral" />
        <Kpi label="TX_NEW (mois)"
             value={isError ? 'Erreur' : formatInt(data?.txNewMonth)}
             hint="Nouvelles initiations ARV"
             hintTone="neutral" />
        <Kpi label="CV supprimée"
             value={isError ? 'Erreur' : formatPercent(data?.viralSuppression ?? null)}
             hint="< 1000 copies/mL · 12 mois"
             hintTone="positive" />
        <Kpi label="Sites en ligne"
             value={isError ? 'Erreur'
                : `${formatInt(data?.sitesOnline)} / ${formatInt(data?.sitesTotalScope)}`}
             hint="Synchronisés < 24h"
             hintTone="neutral" />
      </div>

      {/* Two-column row */}
      <div className="grid gap-3 lg:grid-cols-2">
        <div className="card p-4">
          <h3 className="text-sm font-medium mb-4">File active &middot; 12 mois glissants</h3>
          <div className="h-56">
            {isLoading ? (
              <div className="h-full flex items-center justify-center text-ink-muted text-sm">
                Chargement…
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data?.activeFile ?? []}
                          margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#94a3b8" />
                  <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" />
                  <Tooltip
                    contentStyle={{ borderRadius: 6, fontSize: 12 }}
                    formatter={(v: number) => [formatInt(v), 'Patients']}
                  />
                  <Bar dataKey="count" fill="#009d8e" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        <div className="card p-4">
          <h3 className="text-sm font-medium mb-2">Alertes synchronisation</h3>
          {isLoading ? (
            <p className="text-sm text-ink-muted">Chargement…</p>
          ) : data ? (
            <div className="divide-y divide-slate-100">
              <AlertRow
                label={`${formatInt(data.syncAlerts.sitesNoSync7d)} sites > 7j sans sync`}
                badge="Critique"
                tone="critical" />
              <AlertRow
                label={`${formatInt(data.syncAlerts.sitesNoSync24h)} sites > 24h sans sync`}
                badge="Attention"
                tone="warning" />
              <AlertRow
                label={`${formatInt(data.fileActive)} lignes en file`}
                badge="Globale"
                tone="info" />
              <AlertRow
                label="Dernier batch reçu"
                badge={formatTime(data.syncAlerts.lastBatchAt)}
                tone="ok" />
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
