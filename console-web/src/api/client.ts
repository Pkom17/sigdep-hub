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

// --- Sites -----------------------------------------------------------------

export type SiteStatus = 'all' | 'online' | 'late' | 'offline';

export type SiteRow = {
  id: number;
  code: string;
  name: string;
  facilityType: string | null;
  runsSigdep: boolean | null;
  lastSyncAt: string | null;
  districtId: number;
  districtName: string;
  regionId: number;
  regionName: string;
  patientCount: number;
};

export type SitePage = {
  content: SiteRow[];
  total: number;
  page: number;
  size: number;
};

export type RegionRef = { id: number; name: string };

export function fetchSites(opts: {
  q?: string;
  status?: SiteStatus;
  regionId?: number;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  if (opts.q) params.set('q', opts.q);
  if (opts.status && opts.status !== 'all') params.set('status', opts.status);
  if (opts.regionId) params.set('regionId', String(opts.regionId));
  params.set('page', String(opts.page ?? 0));
  params.set('size', String(opts.size ?? 50));
  return get<SitePage>(`/api/v1/sites?${params}`);
}

export function fetchRegions() {
  return get<RegionRef[]>('/api/v1/sites/regions');
}

// --- Biology ---------------------------------------------------------------

export type MonthlySuppression = {
  month: string;
  total: number;
  suppressed: number;
  pct: number | null;
};

export type Cd4Distribution = {
  lt200: number;
  b200_350: number;
  b350_500: number;
  ge500: number;
  total: number;
};

export type TopTest = { testName: string; count: number };

export type BiologySummary = {
  examsInPeriod: number;
  examsAllTime: number;
  lastExamDate: string | null;
  viralSuppressionPct: number | null;
  monthlySuppression: MonthlySuppression[];
  cd4Distribution: Cd4Distribution;
  topTests: TopTest[];
  periodMonths: number;
};

export type ExamRow = {
  id: number;
  examDate: string | null;
  testName: string;
  valueNumeric: number | null;
  valuePct: number | null;
  valueText: string | null;
  unit: string | null;
  siteCode: string;
  siteName: string;
  patientId: number;
  patientCode: string | null;
};

export function fetchBiologySummary(months: number, regionId?: number) {
  const params = new URLSearchParams();
  params.set('months', String(months));
  if (regionId) params.set('regionId', String(regionId));
  return get<BiologySummary>(`/api/v1/biology/summary?${params}`);
}

export type ExamFilter = 'vl' | 'cd4' | 'all';

export type ExamPage = {
  content: ExamRow[];
  total: number;
  page: number;
  size: number;
};

export function fetchBiologyExams(opts: {
  test: ExamFilter;
  months: number;
  regionId?: number;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  if (opts.test !== 'all') params.set('test', opts.test);
  params.set('months', String(opts.months));
  if (opts.regionId) params.set('regionId', String(opts.regionId));
  params.set('page', String(opts.page ?? 0));
  params.set('size', String(opts.size ?? 50));
  return get<ExamPage>(`/api/v1/biology/exams?${params}`);
}

/**
 * Trigger a CSV download for the given filter, sending the bearer token
 * the same way as a normal API call so the backend can authorize the
 * request and stream the file.
 */
export async function downloadBiologyCsv(opts: {
  test: ExamFilter;
  months: number;
  regionId?: number;
}): Promise<void> {
  const params = new URLSearchParams();
  if (opts.test !== 'all') params.set('test', opts.test);
  params.set('months', String(opts.months));
  if (opts.regionId) params.set('regionId', String(opts.regionId));
  const url = `/api/v1/biology/exams.csv?${params}`;

  const headers: Record<string, string> = {};
  const token = getAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const r = await fetch(url, { headers });
  if (!r.ok) throw new Error(`${r.status} ${r.statusText} on ${url}`);

  const blob = await r.blob();
  const filename = `biology-${opts.test}-${opts.months}m.csv`;
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(a.href);
}
