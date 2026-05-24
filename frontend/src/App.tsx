import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router";
import { router } from "@/router/routes";
import { queryClient } from "@/lib/queryClient";
import { AuthBootstrap } from "@/lib/authBootstrap";
import { ErrorDialogProvider } from "@/lib/error/ErrorDialogProvider";
import { AuthSessionWarningDialog } from "@/components/layout/AuthSessionWarningDialog";

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ErrorDialogProvider>
        <AuthBootstrap />
        <AuthSessionWarningDialog />
        <RouterProvider router={router} />
      </ErrorDialogProvider>
    </QueryClientProvider>
  );
}
