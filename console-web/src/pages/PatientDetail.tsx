import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { fetchPatient, fetchPatientTimeline } from '../api/client';

const KIND_LABEL: Record<string, { label: string; color: string }> = {
  visit:      { label: 'Visite',     color: 'bg-sky-100 text-sky-700' },
  initiation: { label: 'Initiation', color: 'bg-emerald-100 text-emerald-700' },
  lab:        { label: 'Biologie',   color: 'bg-violet-100 text-violet-700' },
};

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR',
    { day: '2-digit', month: 'short', year: 'numeric' });
}

export function PatientDetail() {
  const { id } = useParams<{ id: string }>();
  const patientId = Number(id);

  const patient = useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => fetchPatient(patientId),
    enabled: !Number.isNaN(patientId),
  });

  const timeline = useQuery({
    queryKey: ['patientTimeline', patientId],
    queryFn: () => fetchPatientTimeline(patientId),
    enabled: !Number.isNaN(patientId),
  });

  if (patient.isLoading) {
    return <div className="px-6 py-6 text-ink-muted text-sm">Chargement…</div>;
  }
  if (patient.isError || !patient.data) {
    return <div className="px-6 py-6 text-rose-600 text-sm">Patient introuvable</div>;
  }

  const p = patient.data;

  return (
    <div className="px-6 py-6">
      <div className="mb-4">
        <Link to="/dashboard/patients" className="text-sm text-sigdep-700 hover:underline">
          ← Retour à la liste
        </Link>
      </div>

      <h1 className="text-2xl font-semibold tracking-tight">
        {p.identifiers[0] ?? p.sourceUuid.slice(0, 8)}
      </h1>
      <p className="text-sm text-ink-muted mb-6">
        {p.siteName} ({p.siteCode})
      </p>

      {/* Identity card */}
      <div className="card p-4 mb-6">
        <h3 className="text-sm font-medium mb-3">Identité</h3>
        <dl className="grid gap-x-6 gap-y-2 grid-cols-2 lg:grid-cols-4 text-sm">
          <Field label="Sexe" value={p.sex === 'M' ? 'Homme' : p.sex === 'F' ? 'Femme' : null} />
          <Field label="Date de naissance" value={p.birthDate ? formatDate(p.birthDate) : null} />
          <Field label="Profession" value={p.profession} />
          <Field label="Niveau d’éducation" value={p.educationLevel} />
          <Field label="Statut matrimonial" value={p.maritalStatus} />
          <Field label="Lieu de naissance" value={p.birthPlace} />
          <Field label="Identifiants" value={p.identifiers.join(', ') || null} />
        </dl>
      </div>

      {/* Timeline */}
      <div className="card p-4">
        <h3 className="text-sm font-medium mb-4">Chronologie</h3>
        {timeline.isLoading ? (
          <p className="text-sm text-ink-muted">Chargement…</p>
        ) : !timeline.data || timeline.data.length === 0 ? (
          <p className="text-sm text-ink-muted">Aucun événement</p>
        ) : (
          <ol className="relative border-l-2 border-slate-200 ml-2 space-y-4">
            {timeline.data.map((e, i) => {
              const kind = KIND_LABEL[e.kind] ?? { label: e.kind, color: 'bg-slate-100 text-slate-700' };
              return (
                <li key={i} className="ml-4 pl-2">
                  <span className="absolute -left-1.5 mt-1.5 h-3 w-3 rounded-full bg-sigdep-500 border-2 border-white" />
                  <div className="flex items-baseline gap-2 flex-wrap">
                    <span className="text-xs text-ink-muted tabular-nums">{formatDate(e.date)}</span>
                    <span className={`text-[11px] px-1.5 py-0.5 rounded ${kind.color}`}>{kind.label}</span>
                    <span className="text-sm font-medium">{e.label}</span>
                  </div>
                  {e.detail && e.detail !== '—' && (
                    <p className="text-sm text-ink-muted mt-0.5">{e.detail}</p>
                  )}
                </li>
              );
            })}
          </ol>
        )}
      </div>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <dt className="text-xs text-ink-muted">{label}</dt>
      <dd className="text-ink">{value || '—'}</dd>
    </div>
  );
}
