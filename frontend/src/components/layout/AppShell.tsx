import { Outlet } from "react-router";
import { Topbar } from "./Topbar";
import { SiteFooter } from "./SiteFooter";

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
