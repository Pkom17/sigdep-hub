import { Navigate, Route, Routes } from "react-router-dom";
import { RequireAuth } from "./auth";
import { AppLayout } from "./components/AppLayout";
import { Dashboard } from "./pages/Dashboard";
import { Landing } from "./pages/Landing";
import { PatientDetail } from "./pages/PatientDetail";
import { Patients } from "./pages/Patients";
import { Sites } from "./pages/Sites";
import { Stub } from "./pages/Stub";

export function App() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/" element={<Landing />} />
      <Route path="/public" element={<Landing />} />

      {/* Authenticated app — /app/* */}
      <Route
        path="/app"
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="vue-ensemble" replace />} />
        <Route path="vue-ensemble" element={<Dashboard />} />
        <Route path="pepfar" element={<Stub title="Indicateurs PEPFAR" />} />
        <Route path="patients" element={<Patients />} />
        <Route path="patients/:id" element={<PatientDetail />} />
        <Route path="sites" element={<Sites />} />
        <Route path="clinique" element={<Stub title="Suivi clinique" />} />
        <Route path="pharmacie" element={<Stub title="Pharmacie / ARV" />} />
        <Route path="depistage" element={<Stub title="Dépistage" />} />
        <Route path="ptme" element={<Stub title="PTME" />} />
        <Route path="tpt" element={<Stub title="TPT" />} />
        <Route path="biologie" element={<Stub title="Biologie" />} />
        <Route path="sync" element={<Stub title="Synchronisation" />} />
        <Route path="users" element={<Stub title="Utilisateurs" />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
