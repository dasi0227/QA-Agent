import { Outlet } from "react-router";
import { SiteFooter } from "./SiteFooter";
import { Topbar } from "./Topbar";

export function AppShell() {
    return (
        <div className="app-shell">
            <div className="page-shell">
                <Topbar />
                <div className="app-shell__content">
                    <Outlet />
                </div>
                <SiteFooter />
            </div>
        </div>
    );
}
