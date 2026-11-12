import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loginPageConfig } from '../loginPage.config';
import type { SlideImage } from '../slideshowImages';
import { BackgroundSlideshow } from './BackgroundSlideshow';

const { slideDurationMs, crossfadeMs } = loginPageConfig.slideshow;

function slide(n: number): SlideImage {
  const set = (format: string) =>
    [768, 1280, 1920].map((w) => `/assets/image_${n}-${w}.${format} ${w}w`).join(', ');
  return {
    id: `image_${n}.jpg`,
    srcSets: { avif: set('avif'), webp: set('webp') },
    fallbackSrc: `/assets/image_${n}-1920.webp`,
    width: 1920,
    height: 1280,
  };
}

const slides = (count: number) => Array.from({ length: count }, (_, i) => slide(i + 1));
/** Identity order: slide i is shown i-th. */
const noShuffle = () => 0.999999;

function images(container: HTMLElement) {
  return [...container.querySelectorAll('img')];
}

function imageOf(container: HTMLElement, n: number) {
  return images(container).find((img) => img.getAttribute('src') === `/assets/image_${n}-1920.webp`);
}

function layerOf(container: HTMLElement, n: number) {
  return imageOf(container, n)?.closest('[data-state]');
}

let consoleError: ReturnType<typeof vi.spyOn>;
beforeEach(() => {
  vi.useFakeTimers();
  consoleError = vi.spyOn(console, 'error');
});
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('BackgroundSlideshow', () => {
  it('renders decorative, non-focusable responsive images (spec §6.1)', () => {
    const { container } = render(<BackgroundSlideshow slides={slides(3)} random={noShuffle} />);
    const root = screen.getByTestId('login-background');
    expect(root).toHaveAttribute('aria-hidden', 'true');
    for (const img of images(container)) {
      expect(img).toHaveAttribute('alt', '');
      expect(img).toHaveAttribute('aria-hidden', 'true');
      expect(img).not.toHaveAttribute('tabindex');
      expect(img.getAttribute('srcset')).toMatch(/768w.*1280w.*1920w/);
      expect(img).toHaveAttribute('sizes', '100vw');
    }
    expect(container.querySelectorAll('a, button, input, [tabindex]')).toHaveLength(0);
  });

  it('offers phones only the narrowest variant of each format, best format first', () => {
    const { container } = render(<BackgroundSlideshow slides={slides(2)} random={noShuffle} />);
    const sources = [...container.querySelectorAll('picture')][0].querySelectorAll('source');
    const summary = [...sources].map((s) => [s.getAttribute('type'), s.getAttribute('media'), s.getAttribute('srcset')]);
    expect(summary).toEqual([
      ['image/avif', '(max-width: 767px)', '/assets/image_1-768.avif 768w'],
      ['image/webp', '(max-width: 767px)', '/assets/image_1-768.webp 768w'],
      ['image/avif', null, slide(1).srcSets.avif],
      ['image/webp', null, slide(1).srcSets.webp],
    ]);
    for (const source of sources) expect(source).toHaveAttribute('sizes', '100vw');
  });

  it('mounts only the first and the next photo, with the aurora until the first one has loaded (BG-8, BG-10)', () => {
    const { container } = render(<BackgroundSlideshow slides={slides(11)} random={noShuffle} />);
    expect(images(container)).toHaveLength(2);
    expect(imageOf(container, 1)).toBeDefined();
    expect(imageOf(container, 2)).toBeDefined();
    expect(screen.getByTestId('login-aurora')).toBeInTheDocument();
    expect(layerOf(container, 1)).toHaveAttribute('data-state', 'pending');

    fireEvent.load(imageOf(container, 1)!);
    expect(layerOf(container, 1)).toHaveAttribute('data-state', 'visible');
    // The aurora stays underneath while the first photo fades in, then goes away.
    expect(screen.getByTestId('login-aurora')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(crossfadeMs);
    });
    expect(screen.queryByTestId('login-aurora')).not.toBeInTheDocument();
  });

  it('crossfades with the configured duration, keeping the outgoing photo underneath (BG-3)', () => {
    const { container } = render(<BackgroundSlideshow slides={slides(3)} random={noShuffle} />);
    expect(screen.getByTestId('login-background').style.getPropertyValue('--login-crossfade')).toBe(
      `${crossfadeMs}ms`,
    );
    fireEvent.load(imageOf(container, 1)!);
    fireEvent.load(imageOf(container, 2)!);
    act(() => {
      vi.advanceTimersByTime(slideDurationMs);
    });
    expect(layerOf(container, 2)).toHaveAttribute('data-state', 'visible');
    expect(layerOf(container, 1)).toHaveAttribute('data-state', 'outgoing');
    // The third photo is now being preloaded.
    expect(layerOf(container, 3)).toHaveAttribute('data-state', 'pending');
    act(() => {
      vi.advanceTimersByTime(crossfadeMs);
    });
    expect(imageOf(container, 1)).toBeUndefined();
    expect(images(container)).toHaveLength(2);
  });

  it('shows the aurora and no image at all for an empty list (spec §9.3)', () => {
    const { container } = render(<BackgroundSlideshow slides={[]} />);
    expect(screen.getByTestId('login-aurora')).toBeInTheDocument();
    expect(images(container)).toHaveLength(0);
    expect(consoleError).not.toHaveBeenCalled();
  });

  it('shows the aurora and leaves no broken image when every photo fails (spec §9.3)', () => {
    const { container } = render(<BackgroundSlideshow slides={slides(3)} random={noShuffle} />);
    for (let guard = 0; guard < 5 && images(container).length > 0; guard++) {
      for (const img of images(container)) fireEvent.error(img);
    }
    expect(images(container)).toHaveLength(0);
    expect(screen.getByTestId('login-aurora')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(slideDurationMs * 3);
    });
    expect(screen.getByTestId('login-aurora')).toBeInTheDocument();
    expect(consoleError).not.toHaveBeenCalled();
  });

  it('performs no fetch or XHR besides the image URLs (spec §9.12)', () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const xhrOpen = vi.spyOn(XMLHttpRequest.prototype, 'open');
    const { container, unmount } = render(<BackgroundSlideshow slides={slides(3)} random={noShuffle} />);
    for (const img of images(container)) fireEvent.load(img);
    act(() => {
      vi.advanceTimersByTime(slideDurationMs * 4);
    });
    unmount();
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrOpen).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('leaves no timers after unmount, also in the middle of a crossfade (§9.13)', () => {
    const { container, unmount } = render(<BackgroundSlideshow slides={slides(3)} random={noShuffle} />);
    for (const img of images(container)) fireEvent.load(img);
    act(() => {
      vi.advanceTimersByTime(slideDurationMs);
    });
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
