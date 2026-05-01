import { Outlet } from "react-router";
import { SiteFooter } from "./SiteFooter";

export function AuthLayout() {
  return (
    <div className="app-shell auth-shell">
      <div className="page-shell auth-shell__inner">
        <div className="auth-shell__content">
          <div className="eyebrow auth-shell__eyebrow">QA Agent</div>
          <Outlet />
        </div>
        <SiteFooter />
      </div>
    </div>
  );
}
