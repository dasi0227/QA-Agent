import { LoadingCard } from "@/components/base/loading-card";

export function RouteLoadingCard() {
    return (
        <div className="page-frame">
            <div style={{ width: "min(720px, 100%)", margin: "0 auto" }}>
                <LoadingCard />
            </div>
        </div>
    );
}
