export type NavigationKey = "quiz" | "repository" | "create";

export const navigationItems: Array<{ key: NavigationKey; label: string; to: string }> = [
    { key: "repository", label: "仓库", to: "/repository" },
    { key: "quiz", label: "练习", to: "/quiz" },
    { key: "create", label: "创建", to: "/create" },
];
