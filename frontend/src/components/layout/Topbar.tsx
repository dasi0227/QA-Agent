import { Link } from "react-router";
import { BaseButton, LinkButton } from "@/components/base/button";
import { NavCapsule } from "./NavCapsule";
import { useAuthState } from "@/lib/auth";

export function Topbar() {
  const authState = useAuthState();
  const isAuthenticated = authState.status === "authenticated";
  const displayName =
    authState.user?.displayName?.trim() ||
    authState.user?.username?.trim() ||
    authState.user?.email?.trim() ||
    "用户";

  return (
    <header className="topbar">
      <Link className="avatar" to="/" aria-label="返回首页">
        QA
      </Link>
      <div className="topbar__nav">
        <NavCapsule />
      </div>
      <div className="topbar__actions">
        {authState.status === "loading" ? (
          <BaseButton variant="soft" className="topbar__auth-link" disabled>
            同步中
          </BaseButton>
        ) : isAuthenticated ? (
          <LinkButton to="/profile" variant="soft" className="topbar__auth-link">
            {displayName}
          </LinkButton>
        ) : (
          <LinkButton to="/login" variant="soft" className="topbar__auth-link">
            登录
          </LinkButton>
        )}
      </div>
    </header>
  );
}
