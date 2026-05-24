import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { classifyError } from "@/lib/error/classifyError";
import { emitGlobalError } from "@/lib/error/errorDialogBus";
import { triggerAuthSessionWarning } from "@/components/layout/AuthSessionWarningDialog";
import type { ErrorHandlingMeta } from "@/lib/error/types";

function clearStoredSession() {
    if (typeof window === "undefined") {
        return;
    }
    window.sessionStorage.removeItem("qa-agent.auth.token");
    window.sessionStorage.removeItem("qa-agent.auth.refresh-token");
    window.localStorage.removeItem("qa-agent.auth.token");
    window.localStorage.removeItem("qa-agent.auth.refresh-token");
}

function redirectToLogin() {
    if (typeof window === "undefined") {
        return;
    }
    clearStoredSession();
    triggerAuthSessionWarning({
      reason: "expired",
      title: "需要登录",
      message: "当前页面需要登录后继续访问，如登录状态已失效，将自动返回登录页。",
    });
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error, query) => {
      const meta = (query.meta || {}) as ErrorHandlingMeta;
      const classified = classifyError(error, {
        source: "query",
        errorMode: meta.errorMode,
        errorTitle: meta.errorTitle,
        fallbackTitle: "数据加载失败",
        fallbackMessage: "加载失败，请稍后重试。",
      });

      if (classified.mode === "redirect_login") {
        queryClient.clear();
        redirectToLogin();
        return;
      }

      if (classified.mode === "global_dialog") {
        emitGlobalError({
          code: classified.code,
          title: classified.title,
          message: classified.message,
        });
      }
    },
  }),
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) => {
      const meta = (mutation.meta || {}) as ErrorHandlingMeta;
      const classified = classifyError(error, {
        source: "mutation",
        errorMode: meta.errorMode,
        errorTitle: meta.errorTitle,
        fallbackTitle: "操作失败",
        fallbackMessage: "操作未完成，请稍后重试。",
      });

      if (classified.mode === "redirect_login") {
        queryClient.clear();
        redirectToLogin();
        return;
      }

      if (classified.mode === "global_dialog") {
        emitGlobalError({
          code: classified.code,
          title: classified.title,
          message: classified.message,
        });
      }
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      refetchOnWindowFocus: false,
      retry: 0,
      meta: {
        errorMode: "inline_status_card",
      } satisfies ErrorHandlingMeta,
    },
    mutations: {
      meta: {
        errorMode: "global_dialog",
      } satisfies ErrorHandlingMeta,
    },
  },
});
