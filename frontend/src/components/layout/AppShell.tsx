import { Outlet } from "react-router";
import { DasiChatWidget } from "@/components/dasi/DasiChatWidget";
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
                <DasiChatWidget />
                <SiteFooter />
            </div>
        </div>
    );
}
