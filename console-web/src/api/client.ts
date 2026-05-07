import { getAccessToken } from '../auth';

/**
 * Tiny fetch wrapper. The Vite dev server proxies /api/** to the console-api
 * (see vite.config.ts), so a relative URL works in dev and in prod.
 *
 * Public endpoints (/api/v1/public/**) are called without a token; for every
 * other path we attach the bearer token from the OIDC user store.
 */
async function get<T>(path: string): Promise<T> {
  const headers: Record<string, string> = {};
  if (!path.startsWith('/api/v1/public/')) {
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  const r = await fetch(path, { headers });
  if (!r.ok) throw new Error(`${r.status} ${r.statusText} on ${path}`);
  return r.json() as Promise<T>;
}

// --- Public KPIs (landing) -------------------------------------------------

export type MonthBucket = { month: string; count: number };

export type PublicKpis = {
  patientsActive: number;
  sitesWithData: number;
  viralSuppression: number | null;
  arvCoverage: number | null;
  activeFile: MonthBucket[];
};

export function fetchPublicKpis() {
  return get<PublicKpis>('/api/v1/public/kpis');
}

// --- Dashboard KPIs (authenticated) ---------------------------------------

export type SyncAlerts = {
  sitesNoSync7d: number;
  sitesNoSync24h: number;
  lastBatchAt: string | null;
};

export type DashboardKpis = {
  fileActive: number;
  txNewMonth: number;
  viralSuppression: number | null;
  sitesOnline: number;
  sitesTotalScope: number;
  activeFile: MonthBucket[];
  syncAlerts: SyncAlerts;
};

export function fetchDashboardKpis() {
  return get<DashboardKpis>('/api/v1/dashboard/kpis');
}

// --- Patients --------------------------------------------------------------

export type PatientRow = {
  id: number;
  sourceUuid: string;
  sex: string | null;
  birthDate: string | null;
  siteCode: string;
  siteName: string;
  primaryIdentifier: string | null;
};

export type PatientPage = {
  content: PatientRow[];
  total: number;
  page: number;
  size: number;
};

export type PatientDetail = {
  id: number;
  sourceUuid: string;
  sex: string | null;
  birthDate: string | null;
  profession: string | null;
  educationLevel: string | null;
  maritalStatus: string | null;
  birthPlace: string | null;
  siteCode: string;
  siteName: string;
  identifiers: string[];
};

export type TimelineEntry = {
  date: string;
  kind: 'visit' | 'initiation' | 'lab';
  label: string;
  detail: string | null;
};

export function fetchPatients(q: string, page = 0, size = 25) {
  const params = new URLSearchParams();
  if (q) params.set('q', q);
  params.set('page', String(page));
  params.set('size', String(size));
  return get<PatientPage>(`/api/v1/patients?${params}`);
}

export function fetchPatient(id: number) {
  return get<PatientDetail>(`/api/v1/patients/${id}`);
}

export function fetchPatientTimeline(id: number) {
  return get<TimelineEntry[]>(`/api/v1/patients/${id}/timeline`);
}
