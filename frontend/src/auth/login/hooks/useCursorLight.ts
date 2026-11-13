import { useEffect, type RefObject } from 'react';

export const LIGHT_X = '--login-light-x';
export const LIGHT_Y = '--login-light-y';
/** Set on the element while the pointer is over it; the CSS fades the highlight in. */
export const LIGHT_ATTRIBUTE = 'data-light';

/**
 * AMB-3 cursor-follow light on the sign-in card: the pointer position inside the element is written as CSS custom
 * properties at most once per animation frame (the CSS moves the highlight with `transform`), without React
 * re-renders. Registered only when `enabled` (fine hover pointer, motion allowed); removed on unmount.
 */
export function useCursorLight(target: RefObject<HTMLElement | null>, enabled: boolean) {
  useEffect(() => {
    const element = target.current;
    if (!enabled || !element) return;
    let frame: number | null = null;
    let x = 0;
    let y = 0;

    const write = () => {
      frame = null;
      element.style.setProperty(LIGHT_X, `${Math.round(x)}px`);
      element.style.setProperty(LIGHT_Y, `${Math.round(y)}px`);
    };
    const onPointerMove = (event: PointerEvent) => {
      if (event.pointerType && event.pointerType !== 'mouse') return;
      const rect = element.getBoundingClientRect();
      x = event.clientX - rect.left;
      y = event.clientY - rect.top;
      element.setAttribute(LIGHT_ATTRIBUTE, 'on');
      if (frame === null) frame = requestAnimationFrame(write);
    };
    const onPointerLeave = () => element.removeAttribute(LIGHT_ATTRIBUTE);

    element.addEventListener('pointermove', onPointerMove, { passive: true });
    element.addEventListener('pointerleave', onPointerLeave);
    return () => {
      element.removeEventListener('pointermove', onPointerMove);
      element.removeEventListener('pointerleave', onPointerLeave);
      if (frame !== null) cancelAnimationFrame(frame);
      element.removeAttribute(LIGHT_ATTRIBUTE);
      element.style.removeProperty(LIGHT_X);
      element.style.removeProperty(LIGHT_Y);
    };
  }, [target, enabled]);
}
