import { Outlet } from "react-router";
import { SiteFooter } from "./SiteFooter";

export function FlowShell() {
    return (
        <div className="app-shell app-shell--flow">
            <div className="page-shell page-shell--flow">
                <div className="app-shell__content app-shell__content--flow">
                    <Outlet />
                </div>
                <SiteFooter />
            </div>
        </div>
    );
}
