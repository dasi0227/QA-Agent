import { Navigate, createBrowserRouter } from "react-router";
import { AppShell } from "@/components/layout/AppShell";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { RequireAuth } from "@/components/layout/RequireAuth";
import { IndexPage } from "@/pages/IndexPage";
import { LoginPage } from "@/pages/LoginPage";
import { ProfilePage } from "@/pages/ProfilePage";
import { QuizPage } from "@/pages/QuizPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { QASetPage } from "@/pages/QASetPage";
import { QuestionPage } from "@/pages/QuestionPage";
import { DocumentPage } from "@/pages/DocumentPage";
import { CreatePage } from "@/pages/CreatePage";
import { QAPage } from "@/pages/QAPage";
import { ResultPage } from "@/pages/ResultPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
    ],
  },
  {
    element: <AppShell />,
    children: [
      { index: true, element: <IndexPage /> },
      { path: "/index", element: <Navigate to="/" replace /> },
      { path: "/home", element: <Navigate to="/" replace /> },
      {
        element: <RequireAuth />,
        children: [
          { path: "/profile", element: <ProfilePage /> },
          { path: "/quiz", element: <QuizPage /> },
          { path: "/create", element: <CreatePage /> },
          { path: "/repository", element: <Navigate to="/repository/qa-set" replace /> },
          { path: "/repository/qa-set", element: <QASetPage /> },
          { path: "/repository/qa-set/:id", element: <QASetPage /> },
          { path: "/repository/question", element: <QuestionPage /> },
          { path: "/repository/document", element: <DocumentPage /> },
          { path: "/practice/:sessionId", element: <QAPage /> },
          { path: "/result/:sessionId", element: <ResultPage /> },
          { path: "/qa", element: <Navigate to="/quiz" replace /> },
          { path: "/result", element: <Navigate to="/quiz" replace /> },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
