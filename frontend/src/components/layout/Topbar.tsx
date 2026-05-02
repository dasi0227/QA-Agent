import { Link } from "react-router";
import { BaseButton, LinkButton } from "@/components/base/button";
import { NavCapsule } from "./NavCapsule";
import { useAuthState } from "@/lib/auth";

export function Topbar() {
  const authState = useAuthState();
  const isAuthenticated = authState.status === "authenticated";
  const avatarUrl = authState.user?.avatar?.trim() || "";

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
          <Link
            to="/profile"
            className="avatar"
            aria-label="个人设置"
            style={{
              width: 56,
              height: 56,
              borderRadius: 20,
              overflow: "hidden",
              display: "grid",
              placeItems: "center",
            }}
          >
            {avatarUrl ? (
              <img
                src={avatarUrl}
                alt=""
                style={{ width: "100%", height: "100%", objectFit: "cover" }}
              />
            ) : (
              <span style={{ fontFamily: "var(--font-sans)", fontSize: 14, color: "var(--ink-soft)" }}>
                {authState.user?.username?.charAt(0)?.toUpperCase() || "U"}
              </span>
            )}
          </Link>
        ) : (
          <LinkButton to="/login" variant="soft" className="topbar__auth-link">
            登录
          </LinkButton>
        )}
      </div>
    </header>
  );
}
