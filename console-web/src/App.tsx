import { Routes, Route, Navigate } from 'react-router-dom';
import { Landing } from './pages/Landing';
import { AppLayout } from './components/AppLayout';
import { Dashboard } from './pages/Dashboard';
import { Stub } from './pages/Stub';
import { Patients } from './pages/Patients';
import { PatientDetail } from './pages/PatientDetail';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/dashboard" element={<AppLayout />}>
        <Route index element={<Dashboard />} />
        <Route path="pepfar" element={<Stub title="Indicateurs PEPFAR" />} />
        <Route path="clinique" element={<Stub title="Suivi clinique" />} />
        <Route path="pharmacie" element={<Stub title="Pharmacie / ARV" />} />
        <Route path="depistage" element={<Stub title="Dépistage" />} />
        <Route path="ptme" element={<Stub title="PTME" />} />
        <Route path="tpt" element={<Stub title="TPT" />} />
        <Route path="biologie" element={<Stub title="Biologie" />} />
        <Route path="patients" element={<Patients />} />
        <Route path="patients/:id" element={<PatientDetail />} />
        <Route path="sites" element={<Stub title="Sites" />} />
        <Route path="sync" element={<Stub title="Synchronisation" />} />
        <Route path="users" element={<Stub title="Utilisateurs" />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
