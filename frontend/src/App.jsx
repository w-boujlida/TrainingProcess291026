import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import HomePage from './pages/HomePage';
import UsersPage from './pages/UsersPage';
import AuditPage from './pages/AuditPage';
import RecruitmentPage from './pages/RecruitmentPage';
import HierarchyPage from './pages/HierarchyPage';
import ForbiddenPage from './pages/ForbiddenPage';
import { USER_MANAGERS } from './roles';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="/forbidden" element={<ForbiddenPage />} />
          <Route element={<ProtectedRoute roles={USER_MANAGERS} />}>
            <Route path="/users" element={<UsersPage />} />
          </Route>
          <Route element={<ProtectedRoute roles={['SUPER_ADMIN']} />}>
            <Route path="/audit" element={<AuditPage />} />
          </Route>
          <Route path="/recruitment" element={<Navigate to="/recruitment/formation" replace />} />
          <Route path="/recruitment/hierarchy" element={<HierarchyPage />} />
          <Route path="/recruitment/:category" element={<RecruitmentPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
