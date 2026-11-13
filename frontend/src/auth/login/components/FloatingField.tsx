import { type InputHTMLAttributes, type ReactNode } from 'react';
import classes from './FloatingField.module.css';

interface FloatingFieldProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className' | 'style' | 'children' | 'onChange'> {
  id: string;
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  /** Control inside the field on the right (e.g. the password visibility toggle). */
  rightSection?: ReactNode;
}

/**
 * Text field with a floating label (CARD-3) and an animated focus glow + underline (CARD-4).
 *
 * The label is a real `<label for>` (accessible name unchanged). It rests inside the field and moves above the text
 * when the field is focused (`:focus-within`), has a value (`data-filled`, set from the controlled value) or was
 * autofilled by the browser (`:autofill` / `:-webkit-autofill`, which fire before any input event reaches React),
 * so an autofilled value never overlaps it. Native `<input>` because Mantine's Input overrides `aria-describedby`.
 */
export function FloatingField({ id, label, value, onValueChange, rightSection, required, ...inputProps }: FloatingFieldProps) {
  return (
    <div
      className={classes.field}
      data-filled={value !== '' || undefined}
      data-with-section={rightSection ? true : undefined}
    >
      <input
        {...inputProps}
        id={id}
        required={required}
        className={classes.input}
        value={value}
        onChange={(e) => onValueChange(e.currentTarget.value)}
      />
      <label htmlFor={id} className={classes.label}>
        {label}
        {required && (
          <span className={classes.required} aria-hidden="true">
            {' *'}
          </span>
        )}
      </label>
      <span className={classes.underline} aria-hidden="true" />
      {rightSection && <div className={classes.section}>{rightSection}</div>}
    </div>
  );
}
