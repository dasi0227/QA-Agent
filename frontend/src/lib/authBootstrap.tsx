import { useEffect } from "react";
import { ApiError } from "./api/client";
import { useCurrentUserQuery } from "./api/hooks";
import { clearAccessToken, setAuthStatus, setCurrentUser, useAuthState } from "./auth";

export function AuthBootstrap() {
    const authState = useAuthState();
    const currentUserQuery = useCurrentUserQuery({ enabled: Boolean(authState.token) });

    useEffect(() => {
        if (!authState.token) {
            return;
        }

        if (currentUserQuery.data) {
            setCurrentUser(currentUserQuery.data);
            return;
        }

        if (!currentUserQuery.isError) {
            return;
        }

        if (currentUserQuery.error instanceof ApiError && currentUserQuery.error.status === 401) {
            clearAccessToken();
            return;
        }

        setAuthStatus("unauthenticated");
    }, [authState.token, currentUserQuery.data, currentUserQuery.error, currentUserQuery.isError]);

    return null;
}
