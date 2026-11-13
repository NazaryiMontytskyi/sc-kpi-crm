import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { stubViewport } from '../testing/viewportStub';
import { useMediaQueryMatch } from './useMediaQueryMatch';

describe('useMediaQueryMatch', () => {
  let viewport: ReturnType<typeof stubViewport> | undefined;
  afterEach(() => viewport?.restore());

  it('reads the value synchronously on the first render', () => {
    viewport = stubViewport(1024);
    const renders: boolean[] = [];
    renderHook(() => renders.push(useMediaQueryMatch('(min-width: 64em)')));
    expect(renders[0]).toBe(true);
  });

  it('follows viewport changes and removes its listener on unmount', () => {
    viewport = stubViewport(375);
    const { result, unmount } = renderHook(() => useMediaQueryMatch('(min-width: 64em)'));
    expect(result.current).toBe(false);
    act(() => viewport!.resize(1280));
    expect(result.current).toBe(true);
    act(() => viewport!.resize(1023));
    expect(result.current).toBe(false);
    expect(viewport.listenerCount()).toBeGreaterThan(0);
    unmount();
    expect(viewport.listenerCount()).toBe(0);
  });

  it('reports false where matchMedia is missing', () => {
    const original = window.matchMedia;
    // @ts-expect-error simulating an environment without matchMedia
    window.matchMedia = undefined;
    try {
      expect(renderHook(() => useMediaQueryMatch('(min-width: 64em)')).result.current).toBe(false);
    } finally {
      window.matchMedia = original;
    }
  });
});
