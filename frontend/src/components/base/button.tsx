import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Link } from "react-router";
import { cn } from "@/lib/cn";

type ButtonVariant = "primary" | "ghost" | "soft" | "outline" | "link";

type BaseButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  leadingIcon?: ReactNode;
};

export function BaseButton({
  className,
  variant = "ghost",
  leadingIcon,
  type = "button",
  children,
  ...props
}: BaseButtonProps) {
  return (
    <button
      type={type}
      className={cn("btn", `btn--${variant}`, className)}
      {...props}
    >
      {leadingIcon}
      <span>{children}</span>
    </button>
  );
}

type LinkButtonProps = {
  to: string;
  variant?: ButtonVariant;
  className?: string;
  children: ReactNode;
};

export function LinkButton({ to, variant = "ghost", className, children }: LinkButtonProps) {
  return (
    <Link className={cn("btn", `btn--${variant}`, className)} to={to}>
      <span>{children}</span>
    </Link>
  );
}

type ChoiceButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  selected?: boolean;
  className?: string;
  children: ReactNode;
};

export function ChoiceButton({ selected, className, children, type = "button", ...props }: ChoiceButtonProps) {
  return (
    <button
      className={cn("choice-btn", selected && "choice-btn--active", className)}
      type={type}
      {...props}
    >
      {children}
    </button>
  );
}
