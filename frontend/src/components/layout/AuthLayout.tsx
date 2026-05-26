import { Link, Outlet } from "react-router";
import { SiteFooter } from "./SiteFooter";

export function AuthLayout() {
  return (
    <div className="app-shell auth-shell">
      <div className="page-shell auth-shell__inner">
        <div className="auth-shell__content">
          <Link to="/" className="auth-banner">
            <img className="auth-banner__icon" src="/logo.svg" alt="" />
            <span className="auth-banner__text">QA Agent</span>
          </Link>
          <Outlet />
        </div>
        <SiteFooter />
      </div>
    </div>
  );
}
