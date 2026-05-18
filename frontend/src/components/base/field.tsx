import type { ChangeEvent, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { Children, forwardRef, isValidElement, useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/cn";

type FieldProps = {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
  className?: string;
};

export function Field({ label, hint, error, children, className }: FieldProps) {
  return (
    <label className={cn("field", className)}>
      <span className="field__label">{label}</span>
      {hint ? <span className="field__hint">{hint}</span> : null}
      {children}
      {error ? <span className="field__error">{error}</span> : null}
    </label>
  );
}

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function TextInput({ className, ...props }, ref) {
    return <input ref={ref} className={cn("input", className)} {...props} />;
  },
);

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function TextArea({ className, ...props }, ref) {
    return <textarea ref={ref} className={cn("textarea", className)} {...props} />;
  },
);

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, children, ...props }, ref) {
    const { value, onChange, disabled, ...restProps } = props;
    const [open, setOpen] = useState(false);
    const wrapperRef = useRef<HTMLSpanElement | null>(null);
    const normalizedValue = typeof value === "string" ? value : value == null ? "" : String(value);
    const options = useMemo(() => {
      return Children.toArray(children).flatMap((child) => {
        if (!isValidElement(child)) return [];
        const optionValue = child.props.value == null ? "" : String(child.props.value);
        const label = typeof child.props.children === "string"
          ? child.props.children
          : Children.toArray(child.props.children).join("");
        return [{ value: optionValue, label, disabled: Boolean(child.props.disabled) }];
      });
    }, [children]);
    const selectedOption = options.find((option) => option.value === normalizedValue) ?? options[0];

    useEffect(() => {
      if (!open) return;
      const handlePointerDown = (event: MouseEvent) => {
        if (!wrapperRef.current?.contains(event.target as Node)) {
          setOpen(false);
        }
      };
      const handleEscape = (event: KeyboardEvent) => {
        if (event.key === "Escape") {
          setOpen(false);
        }
      };
      document.addEventListener("mousedown", handlePointerDown);
      document.addEventListener("keydown", handleEscape);
      return () => {
        document.removeEventListener("mousedown", handlePointerDown);
        document.removeEventListener("keydown", handleEscape);
      };
    }, [open]);

    const commitValue = (nextValue: string) => {
      if (!onChange) return;
      const event = {
        target: { value: nextValue },
        currentTarget: { value: nextValue },
      } as ChangeEvent<HTMLSelectElement>;
      onChange(event);
    };

    return (
      <span ref={wrapperRef} className={cn("select-field", open && "select-field--open", disabled && "select-field--disabled")}>
        <select
          ref={ref}
          className={cn("input input--select", className)}
          value={normalizedValue}
          disabled={disabled}
          tabIndex={-1}
          aria-hidden="true"
          {...restProps}
        >
          {children}
        </select>
        <button
          type="button"
          className={cn("input input--select-trigger", className)}
          disabled={disabled}
          onClick={() => setOpen((current) => !current)}
        >
          <span>{selectedOption?.label ?? ""}</span>
        </button>
        <span className="select-field__icon" aria-hidden="true">
          <ChevronDown size={16} />
        </span>
        {open ? (
          <span className="select-field__menu" role="listbox" aria-hidden={disabled}>
            {options.map((option) => (
              <button
                key={`${option.value}-${option.label}`}
                type="button"
                role="option"
                disabled={option.disabled}
                aria-selected={option.value === normalizedValue}
                className={cn("select-field__option", option.value === normalizedValue && "select-field__option--active")}
                onClick={() => {
                  if (option.disabled) return;
                  commitValue(option.value);
                  setOpen(false);
                }}
              >
                {option.label}
              </button>
            ))}
          </span>
        ) : null}
      </span>
    );
  },
);

export function ToggleField({
  label,
  checked,
  onChange,
  hint,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  hint?: string;
}) {
  return (
    <button
      type="button"
      className={cn("toggle-field", checked && "toggle-field--active")}
      onClick={() => onChange(!checked)}
    >
      <span>
        <strong>{label}</strong>
        {hint ? <small>{hint}</small> : null}
      </span>
      <span className="toggle-field__switch" aria-hidden="true">
        <span className="toggle-field__thumb" />
      </span>
    </button>
  );
}
