import { lazy, Suspense, type ReactElement } from "react";
import { Navigate, createBrowserRouter } from "react-router";
import { AppShell } from "@/components/layout/AppShell";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { FlowShell } from "@/components/layout/FlowShell";
import { RequireAuth } from "@/components/layout/RequireAuth";
import { RouteLoadingCard } from "@/components/layout/RouteLoadingCard";

const IndexPage = lazy(() => import("@/pages/IndexPage").then((module) => ({ default: module.IndexPage })));
const LoginPage = lazy(() => import("@/pages/LoginPage").then((module) => ({ default: module.LoginPage })));
const ProfilePage = lazy(() => import("@/pages/ProfilePage").then((module) => ({ default: module.ProfilePage })));
const QuizPage = lazy(() => import("@/pages/QuizPage").then((module) => ({ default: module.QuizPage })));
const RegisterPage = lazy(() => import("@/pages/RegisterPage").then((module) => ({ default: module.RegisterPage })));
const QASetPage = lazy(() => import("@/pages/QASetPage").then((module) => ({ default: module.QASetPage })));
const QuestionPage = lazy(() => import("@/pages/QuestionPage").then((module) => ({ default: module.QuestionPage })));
const DocumentPage = lazy(() => import("@/pages/DocumentPage").then((module) => ({ default: module.DocumentPage })));
const CreatePage = lazy(() => import("@/pages/CreatePage").then((module) => ({ default: module.CreatePage })));
const PracticePage = lazy(() => import("@/pages/PracticePage").then((module) => ({ default: module.PracticePage })));
const ResultPage = lazy(() => import("@/pages/ResultPage").then((module) => ({ default: module.ResultPage })));
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage").then((module) => ({ default: module.NotFoundPage })));

function withRouteLoading(element: ReactElement) {
  return (
    <Suspense fallback={<RouteLoadingCard />}>
      {element}
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: "/login", element: withRouteLoading(<LoginPage />) },
      { path: "/register", element: withRouteLoading(<RegisterPage />) },
    ],
  },
  {
    element: <AppShell />,
    children: [
      { index: true, element: withRouteLoading(<IndexPage />) },
      { path: "/index", element: <Navigate to="/" replace /> },
      { path: "/home", element: <Navigate to="/" replace /> },
      {
        element: <RequireAuth />,
        children: [
          { path: "/profile", element: withRouteLoading(<ProfilePage />) },
          { path: "/quiz", element: withRouteLoading(<QuizPage />) },
          { path: "/create", element: withRouteLoading(<CreatePage />) },
          { path: "/repository", element: <Navigate to="/repository/qa-set" replace /> },
          { path: "/repository/qa-set", element: withRouteLoading(<QASetPage />) },
          { path: "/repository/qa-set/:id", element: withRouteLoading(<QASetPage />) },
          { path: "/repository/question", element: withRouteLoading(<QuestionPage />) },
          { path: "/repository/document", element: withRouteLoading(<DocumentPage />) },
          { path: "/qa", element: <Navigate to="/quiz" replace /> },
        ],
      },
      { path: "*", element: withRouteLoading(<NotFoundPage />) },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <FlowShell />,
        children: [
          { path: "/practice/:sessionId", element: withRouteLoading(<PracticePage />) },
          { path: "/practice/:sessionId/result", element: withRouteLoading(<ResultPage />) },
          { path: "/result/:sessionId", element: withRouteLoading(<ResultPage />) },
          { path: "/result", element: <Navigate to="/quiz" replace /> },
        ],
      },
    ],
  },
]);
