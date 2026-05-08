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
  codeArv: string | null;
  upid: string | null;
  arvInitDate: string | null;
  arvRegimenInitial: string | null;
  lastVisitDate: string | null;
  lastArvRegimen: string | null;
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

export type Observation = { label: string; value: string };

export type DateBlock = {
  date: string;
  kind: 'visit' | 'initiation' | 'closure' | 'lab';
  label: string;
  observations: Observation[];
};

export type EncounterDay = {
  date: string;
  blocks: DateBlock[];
};

export function fetchPatients(q: string, page = 0, size = 25) {
  const params = new URLSearchParams();
  if (q) params.set('q', q);
  params.set('page', String(page));
  params.set('size', String(size));
  return get<PatientPage>(`/api/v1/patients?${params}`);
}

export async function downloadPatientsCsv(q: string): Promise<void> {
  const params = new URLSearchParams();
  if (q) params.set('q', q);
  const url = `/api/v1/patients/list.csv?${params}`;
  await downloadCsv(url, 'patients.csv');
}

export function fetchPatient(id: number) {
  return get<PatientDetail>(`/api/v1/patients/${id}`);
}

export function fetchPatientEncounters(id: number) {
  return get<EncounterDay[]>(`/api/v1/patients/${id}/encounters`);
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
  const filename = `biology-${opts.test}-${opts.months}m.csv`;
  await downloadCsv(url, filename);
}

async function downloadCsv(url: string, filename: string): Promise<void> {
  const headers: Record<string, string> = {};
  const token = getAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const r = await fetch(url, { headers });
  if (!r.ok) throw new Error(`${r.status} ${r.statusText} on ${url}`);
  const blob = await r.blob();
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(a.href);
}

// --- PEPFAR ----------------------------------------------------------------

export type DisaggCell = { sex: string | null; ageBand: string; count: number };
export type Disaggregated = { total: number; cells: DisaggCell[] };
export type TxPvls = {
  denominator: Disaggregated;
  numerator: Disaggregated;
  pct: number | null;
};
export type QuarterRange = {
  fiscalYear: number;
  quarter: number;
  start: string;
  end: string;
};
export type PepfarReport = {
  period: QuarterRange;
  txNew: Disaggregated;
  txCurr: Disaggregated;
  txPvls: TxPvls;
};

export function fetchPepfarReport(fy: number, q: number, regionId?: number) {
  const params = new URLSearchParams();
  params.set('fy', String(fy));
  params.set('q', String(q));
  if (regionId) params.set('regionId', String(regionId));
  return get<PepfarReport>(`/api/v1/pepfar/report?${params}`);
}

export async function downloadPepfarCsv(fy: number, q: number, regionId?: number): Promise<void> {
  const params = new URLSearchParams();
  params.set('fy', String(fy));
  params.set('q', String(q));
  if (regionId) params.set('regionId', String(regionId));
  const url = `/api/v1/pepfar/report.csv?${params}`;
  const filename = `pepfar-FY${fy}Q${q}.csv`;
  await downloadCsv(url, filename);
}

// --- TPT -------------------------------------------------------------------

export type YearBucket = { year: number; count: number };
export type Bucket = { label: string; count: number };

export type TptSummary = {
  totalAllTime: number;
  startedInPeriod: number;
  completedInPeriod: number;
  ongoing: number;
  completionPct: number | null;
  yearly: YearBucket[];
  outcomes: Bucket[];
  adherence: Bucket[];
  statuses: Bucket[];
  regimens: Bucket[];
  periodMonths: number;
};

export type TptRecord = {
  id: number;
  recordDate: string | null;
  followupDate: string | null;
  endDate: string | null;
  outcome: string | null;
  orderNumber: string | null;
  tptStatus: string | null;
  tptRegimen: string | null;
  adherence: string | null;
  weightKg: number | null;
  nextVisitDate: string | null;
  patientId: number;
  patientCode: string | null;
  siteCode: string;
  siteName: string;
};

export type TptRecordPage = {
  content: TptRecord[];
  total: number;
  page: number;
  size: number;
};

export function fetchTptSummary(months: number, regionId?: number) {
  const params = new URLSearchParams();
  params.set('months', String(months));
  if (regionId) params.set('regionId', String(regionId));
  return get<TptSummary>(`/api/v1/tpt/summary?${params}`);
}

export function fetchTptRecords(opts: {
  months: number;
  regionId?: number;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  params.set('months', String(opts.months));
  if (opts.regionId) params.set('regionId', String(opts.regionId));
  params.set('page', String(opts.page ?? 0));
  params.set('size', String(opts.size ?? 50));
  return get<TptRecordPage>(`/api/v1/tpt/records?${params}`);
}

export async function downloadTptCsv(months: number, regionId?: number): Promise<void> {
  const params = new URLSearchParams();
  params.set('months', String(months));
  if (regionId) params.set('regionId', String(regionId));
  const url = `/api/v1/tpt/records.csv?${params}`;
  await downloadCsv(url, `tpt-${months}m.csv`);
}

// --- Clinic (suivi clinique) -----------------------------------------------

export type MonthlyCount = { month: string; count: number };

export type ClinicSummary = {
  visitsAllTime: number;
  visitsInPeriod: number;
  withTbScreening: number;
  withWhoStage: number;
  tbScreeningPct: number | null;
  whoStagePct: number | null;
  monthly: MonthlyCount[];
  whoStageDistribution: Bucket[];
  tbScreeningDistribution: Bucket[];
  arvRegimenDistribution: Bucket[];
  periodMonths: number;
};

export type VisitRow = {
  id: number;
  visitDate: string | null;
  nextVisitDate: string | null;
  sourceForm: string | null;
  arvRegimen: string | null;
  arvTreatmentDays: number | null;
  cotrimTreatmentDays: number | null;
  weightKg: number | null;
  heightCm: number | null;
  bmi: number | null;
  viralLoad: number | null;
  cd4Count: number | null;
  tptStatus: string | null;
  tptRegimen: string | null;
  whoStage: string | null;
  tbScreening: string | null;
  patientId: number;
  patientCode: string | null;
  siteCode: string;
  siteName: string;
};

export type VisitPage = {
  content: VisitRow[];
  total: number;
  page: number;
  size: number;
};

export function fetchClinicSummary(months: number, regionId?: number) {
  const params = new URLSearchParams();
  params.set('months', String(months));
  if (regionId) params.set('regionId', String(regionId));
  return get<ClinicSummary>(`/api/v1/clinic/summary?${params}`);
}

export function fetchClinicVisits(opts: {
  months: number;
  regionId?: number;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  params.set('months', String(opts.months));
  if (opts.regionId) params.set('regionId', String(opts.regionId));
  params.set('page', String(opts.page ?? 0));
  params.set('size', String(opts.size ?? 50));
  return get<VisitPage>(`/api/v1/clinic/visits?${params}`);
}

export async function downloadClinicCsv(months: number, regionId?: number): Promise<void> {
  const params = new URLSearchParams();
  params.set('months', String(months));
  if (regionId) params.set('regionId', String(regionId));
  const url = `/api/v1/clinic/visits.csv?${params}`;
  await downloadCsv(url, `clinique-${months}m.csv`);
}
