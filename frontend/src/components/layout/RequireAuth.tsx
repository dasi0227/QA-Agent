import { Navigate, Outlet, useLocation } from "react-router";
import { useAuthState } from "@/lib/auth";

export function RequireAuth() {
    const authState = useAuthState();
    const location = useLocation();

    if (authState.status === "loading") {
        return null;
    }

    if (authState.status !== "authenticated") {
        return <Navigate to="/login" replace state={{ from: location }} />;
    }

    return <Outlet />;
}
