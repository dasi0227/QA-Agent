import { useEffect } from "react";
import { Outlet, useLocation } from "react-router";
import { useAuthState } from "@/lib/auth";
import { triggerAuthSessionWarning } from "./AuthSessionWarningDialog";

export function RequireAuth() {
    const authState = useAuthState();
    const location = useLocation();

    useEffect(() => {
        if (authState.status === "loading" || authState.status === "authenticated") {
            return;
        }

        triggerAuthSessionWarning({
            reason: "unauthenticated",
            title: "需要登录",
            message: "当前页面需要登录后继续访问，如登录状态已失效，将自动返回登录页。",
        });
    }, [authState.status, location.hash, location.pathname, location.search]);

    if (authState.status === "loading") {
        return null;
    }

    if (authState.status !== "authenticated") {
        return null;
    }

    return <Outlet />;
}
