import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Bar,
  BarChart,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Download, Stethoscope } from "lucide-react";
import {
  downloadClinicCsv,
  fetchClinicSummary,
  fetchClinicVisits,
} from "../api/client";
import { Kpi, formatInt, formatPercent } from "../components/Kpi";
import { PageHeader } from "../components/PageHeader";
import { GeoFilter, GeoScope } from "../components/GeoFilter";
import { SortableTh, SortState } from "../components/SortableTh";
import { ChartSkeleton, KpiRowSkeleton, TableSkeleton } from "../components/Skeleton";

const PERIODS = [
  { months: 12, label: "12 derniers mois" },
  { months: 24, label: "24 derniers mois" },
  { months: 60, label: "5 dernières années" },
];

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("fr-FR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

function shortenWho(s: string | null): string {
  if (!s) return "—";
  // "WHO STAGE 2 ADULT" → "Stade 2"
  const m = s.match(/STAGE\s+(\d)/i);
  return m ? `Stade ${m[1]}` : s;
}

export function Clinique() {
  const [months, setMonths] = useState(12);
  const [scope, setScope] = useState<GeoScope>({});
  const [sort, setSort] = useState<SortState>(null);
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const size = 50;

  const summary = useQuery({
    queryKey: ["clinic-summary", months, scope],
    queryFn: () => fetchClinicSummary(months, scope),
  });
  const visits = useQuery({
    queryKey: ["clinic-visits", months, scope, sort, page],
    queryFn: () => fetchClinicVisits({ months, ...scope, sort, page, size }),
  });

  const onSort = (s: SortState) => { setSort(s); setPage(0); };

  const totalPages = visits.data
    ? Math.max(1, Math.ceil(visits.data.total / visits.data.size))
    : 1;

  async function handleExport() {
    setExporting(true);
    try {
      await downloadClinicCsv(months, scope);
    } catch (err) {
      /* eslint-disable-next-line no-console */ console.error(err);
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="px-6 py-6">
      <PageHeader
        icon={Stethoscope}
        title="Suivi clinique"
        subtitle="Visites de suivi PEC · stades OMS, dépistage TB, régimes ARV"
        right={<>
          <GeoFilter value={scope} onChange={s => { setScope(s); setPage(0); }} />
          <select
            value={months}
            onChange={(e) => {
              setMonths(Number(e.target.value));
              setPage(0);
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm bg-white"
          >
            {PERIODS.map((p) => (
              <option key={p.months} value={p.months}>{p.label}</option>
            ))}
          </select>
          <button
            onClick={handleExport}
            disabled={exporting || !visits.data || visits.data.total === 0}
            className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 px-3 py-1.5 text-xs
                       hover:bg-slate-50 disabled:opacity-50 transition"
          >
            <Download className="h-3.5 w-3.5" />
            {exporting ? "Export…" : "Exporter CSV"}
          </button>
        </>} />

      {/* KPIs */}
      {summary.isLoading ? <KpiRowSkeleton /> : (
        <div className="grid gap-3 grid-cols-2 lg:grid-cols-4 mb-6">
          <Kpi
            label="Visites (cumul)"
            value={summary.isError ? "Erreur" : formatInt(summary.data?.visitsAllTime)}
            hint="Toutes périodes"
            hintTone="neutral"
          />
          <Kpi
            label="Visites (période)"
            value={summary.isError ? "Erreur" : formatInt(summary.data?.visitsInPeriod)}
            hint={`${PERIODS.find((p) => p.months === months)?.label}`}
            hintTone="neutral"
          />
          <Kpi
            label="% dépistage TB"
            value={summary.isError
                ? "Erreur"
                : formatPercent(summary.data?.tbScreeningPct ?? null)}
            hint="Visites avec résultat documenté"
            hintTone="positive"
          />
          <Kpi
            label="% stade OMS"
            value={summary.isError
                ? "Erreur"
                : formatPercent(summary.data?.whoStagePct ?? null)}
            hint="Visites avec stade renseigné"
            hintTone="positive"
          />
        </div>
      )}

      {/* Monthly visits chart */}
      <div className="card p-4 mb-6">
        <h3 className="text-sm font-medium mb-4">Visites par mois</h3>
        <div className="h-48">
          {summary.isLoading ? (
            <ChartSkeleton height="h-48" />
          ) : !summary.data || summary.data.monthly.length === 0 ? (
            <div className="h-full flex items-center justify-center text-ink-muted text-sm">
              —
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={summary.data.monthly}
                margin={{ top: 24, right: 8, left: 0, bottom: 0 }}
              >
                <XAxis
                  dataKey="month"
                  tick={{ fontSize: 11 }}
                  stroke="#94a3b8"
                />
                <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" />
                <Tooltip
                  contentStyle={{ borderRadius: 6, fontSize: 12 }}
                  formatter={(v) => [formatInt(v as number), "Visites"]}
                />
                <Bar dataKey="count" fill="#009d8e" radius={[3, 3, 0, 0]}>
                  <LabelList dataKey="count" position="top"
                             style={{ fill: '#475569', fontSize: 11, fontWeight: 500 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Distributions row */}
      <div className="grid gap-3 lg:grid-cols-3 mb-6">
        <DistributionCard
          title="Stades OMS"
          rows={summary.data?.whoStageDistribution ?? []}
          formatter={shortenWho}
        />
        <DistributionCard
          title="Dépistage TB"
          rows={summary.data?.tbScreeningDistribution ?? []}
        />
        <DistributionCard
          title="Régimes ARV (top 10)"
          rows={summary.data?.arvRegimenDistribution ?? []}
        />
      </div>

      {/* Visits table */}
      <div className="card overflow-hidden">
        <div className="px-4 py-3 bg-sigdep-50 border-b border-sigdep-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-sigdep-800">Visites</h3>
          <span className="text-xs text-ink-muted">
            {visits.data ? `${formatInt(visits.data.total)} visites` : "—"}
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="thead-sigdep text-left">
              <tr className="text-left">
                <SortableTh k="date"       sort={sort} onSort={onSort}>Date</SortableTh>
                <SortableTh k="patient"    sort={sort} onSort={onSort}>Patient</SortableTh>
                <th className="px-4 py-2 font-medium">Stade</th>
                <SortableTh k="arvRegimen" sort={sort} onSort={onSort}>Régime ARV</SortableTh>
                <SortableTh k="weight"     sort={sort} onSort={onSort} align="right">Poids</SortableTh>
                <SortableTh k="bmi"        sort={sort} onSort={onSort} align="right">IMC</SortableTh>
                <th className="px-4 py-2 font-medium">TPT</th>
                <SortableTh k="site"       sort={sort} onSort={onSort}>Site</SortableTh>
              </tr>
            </thead>
            {visits.isLoading ? (
              <TableSkeleton rows={8} cols={8} />
            ) : (
            <tbody className="divide-y divide-slate-100">
              {(() => {
                if (visits.isError) {
                  return (
                    <tr>
                      <td
                        colSpan={8}
                        className="px-4 py-6 text-center text-rose-600"
                      >
                        Erreur de chargement
                      </td>
                    </tr>
                  );
                }
                if (!visits.data || visits.data.content.length === 0) {
                  return (
                    <tr>
                      <td
                        colSpan={8}
                        className="px-4 py-6 text-center text-ink-muted"
                      >
                        Aucune visite
                      </td>
                    </tr>
                  );
                }
                return visits.data.content.map((v) => (
                  <tr key={v.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2 whitespace-nowrap text-ink-muted">
                      {formatDate(v.visitDate)}
                    </td>
                    <td className="px-4 py-2">
                      <Link
                        to={`/app/patients/${v.patientId}`}
                        className="text-sigdep-700 hover:underline font-mono text-xs"
                      >
                        {v.patientCode ?? `#${v.patientId}`}
                      </Link>
                    </td>
                    <td className="px-4 py-2">{shortenWho(v.whoStage)}</td>
                    <td className="px-4 py-2">{v.arvRegimen ?? "—"}</td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {v.weightKg ?? "—"}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {v.bmi ?? "—"}
                    </td>
                    <td className="px-4 py-2 text-ink-muted">
                      {v.tptStatus ? (
                        <span className="text-xs">
                          {v.tptStatus}
                          {v.tptRegimen ? ` (${v.tptRegimen})` : ""}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="px-4 py-2 text-ink-muted">
                      <span className="font-mono text-xs">{v.siteCode}</span>{" "}
                      {v.siteName}
                    </td>
                  </tr>
                ));
              })()}
            </tbody>
            )}
          </table>
        </div>

        {visits.data && visits.data.total > size && (
          <div className="px-4 py-3 flex items-center justify-between text-sm border-t border-slate-100">
            <p className="text-ink-muted">
              Page {visits.data.page + 1} / {totalPages}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={visits.data.page === 0}
                className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50"
              >
                Précédent
              </button>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={visits.data.page + 1 >= totalPages}
                className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50"
              >
                Suivant
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function DistributionCard({
  title,
  rows,
  formatter,
}: {
  title: string;
  rows: { label: string; count: number }[];
  formatter?: (s: string | null) => string;
}) {
  return (
    <div className="card p-4">
      <h3 className="text-sm font-medium mb-3">{title}</h3>
      <table className="w-full text-sm">
        <thead className="text-ink-muted">
          <tr className="text-left">
            <th className="py-1 font-medium">Valeur</th>
            <th className="py-1 font-medium text-right">Nombre</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.length === 0 ? (
            <tr>
              <td colSpan={2} className="py-2 text-center text-ink-muted">
                —
              </td>
            </tr>
          ) : (
            rows.map((r) => (
              <tr key={r.label}>
                <td className="py-1">
                  {formatter ? formatter(r.label) : r.label}
                </td>
                <td className="py-1 text-right tabular-nums">
                  {formatInt(r.count)}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
