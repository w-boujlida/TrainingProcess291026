import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import HomePage from './pages/HomePage';
import UsersPage from './pages/UsersPage';
import RecruitmentPage from './pages/RecruitmentPage';
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
          <Route path="/recruitment" element={<Navigate to="/recruitment/cadre" replace />} />
          <Route path="/recruitment/:category" element={<RecruitmentPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
