import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

type GlassCardProps = HTMLAttributes<HTMLDivElement> & {
  children: ReactNode;
  compact?: boolean;
};

export function GlassCard({ className, compact, children, ...props }: GlassCardProps) {
  return (
    <div
      className={cn("glass-card", compact && "glass-card--compact", className)}
      {...props}
    >
      {children}
    </div>
  );
}

type MetricCardProps = {
  label: string;
  value: string;
  detail?: string;
};

export function MetricCard({ label, value, detail }: MetricCardProps) {
  return (
    <article className="metric-card">
      <div className="metric-card__label">{label}</div>
      <div className="metric-card__value">{value}</div>
      {detail ? <div className="metric-card__detail">{detail}</div> : null}
    </article>
  );
}
