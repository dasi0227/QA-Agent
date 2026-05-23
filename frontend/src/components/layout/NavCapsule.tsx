import { NavLink } from "react-router";
import { navigationItems } from "@/lib/navigation";
import { cn } from "@/lib/cn";

export function NavCapsule() {
  return (
    <nav className="nav-capsule" aria-label="主导航">
        {navigationItems.map((item) => (
        <NavLink
          key={item.key}
          to={item.to}
          className={({ isActive }) => cn("nav-link", isActive && "nav-link--active")}
          end={item.key !== "repository" && item.key !== "create"}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
