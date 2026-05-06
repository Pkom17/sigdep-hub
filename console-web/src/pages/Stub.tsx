type Props = { title: string; subtitle?: string };

export function Stub({ title, subtitle }: Props) {
  return (
    <div className="px-6 py-6">
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
      {subtitle && <p className="text-sm text-ink-muted mt-1">{subtitle}</p>}
      <div className="card p-8 mt-6 text-center text-ink-muted">
        À venir
      </div>
    </div>
  );
}
