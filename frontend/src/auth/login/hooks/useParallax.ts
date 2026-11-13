import { useEffect, type RefObject } from 'react';
import { parallaxTargets, stepOffsets, ZERO_OFFSETS, type ParallaxOffsets } from '../parallax';

/** Custom properties written on the two layers; their CSS turns them into a `translate3d` (transform only). */
export const PARALLAX_X = '--login-parallax-x';
export const PARALLAX_Y = '--login-parallax-y';

function write(element: HTMLElement | null, x: number, y: number) {
  element?.style.setProperty(PARALLAX_X, `${x.toFixed(2)}px`);
  element?.style.setProperty(PARALLAX_Y, `${y.toFixed(2)}px`);
}

function clear(element: HTMLElement | null) {
  element?.style.removeProperty(PARALLAX_X);
  element?.style.removeProperty(PARALLAX_Y);
}

/**
 * AMB-1 mouse parallax: the background layer drifts opposite to the pointer (up to 12 px), the hero with it (up to
 * 6 px). A `pointermove` listener only records the target; a requestAnimationFrame loop lerps towards it and writes
 * CSS custom properties straight onto the elements, so moving the pointer never re-renders React. The loop sleeps
 * once settled and while the tab is hidden. With `enabled` false (touch / coarse pointer, reduced motion, the user's
 * pause) nothing is registered at all. Everything is removed on unmount.
 */
export function useParallax(
  background: RefObject<HTMLElement | null>,
  hero: RefObject<HTMLElement | null>,
  enabled: boolean,
) {
  useEffect(() => {
    if (!enabled) return;
    const backgroundElement = background.current;
    const heroElement = hero.current;
    let current: ParallaxOffsets = { ...ZERO_OFFSETS };
    let target: ParallaxOffsets = { ...ZERO_OFFSETS };
    let frame: number | null = null;

    const schedule = () => {
      if (frame === null && document.visibilityState !== 'hidden') frame = requestAnimationFrame(tick);
    };
    function tick() {
      frame = null;
      const { next, settled } = stepOffsets(current, target);
      current = next;
      write(backgroundElement, current.bgX, current.bgY);
      write(heroElement, current.heroX, current.heroY);
      if (!settled) schedule();
    }
    const onPointerMove = (event: PointerEvent) => {
      // A touch or pen on a hybrid device never drives the parallax.
      if (event.pointerType && event.pointerType !== 'mouse') return;
      target = parallaxTargets(event.clientX, event.clientY, window.innerWidth, window.innerHeight);
      schedule();
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        if (frame !== null) cancelAnimationFrame(frame);
        frame = null;
      } else {
        schedule();
      }
    };

    window.addEventListener('pointermove', onPointerMove, { passive: true });
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      window.removeEventListener('pointermove', onPointerMove);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      if (frame !== null) cancelAnimationFrame(frame);
      frame = null;
      clear(backgroundElement);
      clear(heroElement);
    };
  }, [background, hero, enabled]);
}
