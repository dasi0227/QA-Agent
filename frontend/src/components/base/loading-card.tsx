import { cn } from "@/lib/cn";

type LoadingCardProps = {
    message?: string;
    className?: string;
};

export function LoadingCard({ message = "页面加载中...", className }: LoadingCardProps) {
    return (
        <div className={cn("status-card", className)}>
            <strong>{message}</strong>
        </div>
    );
}
