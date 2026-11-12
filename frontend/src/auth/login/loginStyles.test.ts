import { describe, expect, it } from 'vitest';
import { loginPageConfig } from './loginPage.config';

/**
 * Static checks of the sign-in page CSS modules (MAIN_PAGE_FEATURE.md §6.2, CLAUDE.md "Brand"): colours only through
 * Mantine variables, and animations/transitions limited to compositor-friendly properties.
 *
 * The files are read from disk: with `css: false` in the test config, Vite hands CSS imports (even `?raw`) to tests
 * as empty modules. The app tsconfig has no Node types, hence the minimal typed access to the built-in modules.
 */
interface DirEntry {
  name: string;
  isDirectory(): boolean;
}
interface NodeBuiltins {
  process: {
    cwd(): string;
    getBuiltinModule(id: 'node:fs'): {
      readdirSync(dir: string, options: { withFileTypes: true }): DirEntry[];
      readFileSync(path: string, encoding: 'utf8'): string;
    };
  };
}
const { process } = globalThis as unknown as NodeBuiltins;
const { readdirSync, readFileSync } = process.getBuiltinModule('node:fs');

// Vitest runs from the frontend root (vite.config.ts).
const LOGIN_DIR = 'src/auth/login';

function cssModules(dir: string): string[] {
  return readdirSync(`${process.cwd()}/${dir}`, { withFileTypes: true }).flatMap((entry) => {
    const path = `${dir}/${entry.name}`;
    if (entry.isDirectory()) return cssModules(path);
    return entry.name.endsWith('.module.css') ? [path] : [];
  });
}

const styles: Record<string, string> = Object.fromEntries(
  cssModules(LOGIN_DIR).map((path) => [
    path.slice(LOGIN_DIR.length + 1),
    readFileSync(`${process.cwd()}/${path}`, 'utf8'),
  ]),
);

const ANIMATABLE = new Set(['transform', 'opacity', 'filter']);
const HEX = /#[0-9a-fA-F]{3,8}\b/;

function stripComments(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, '');
}

/** Splits on commas that are not inside parentheses. */
function splitTopLevel(value: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let current = '';
  for (const ch of value) {
    if (ch === '(') depth++;
    if (ch === ')') depth--;
    if (ch === ',' && depth === 0) {
      parts.push(current.trim());
      current = '';
    } else current += ch;
  }
  if (current.trim()) parts.push(current.trim());
  return parts;
}

/** Properties named by every `transition` / `transition-property` declaration. */
function transitionedProperties(css: string): string[] {
  const props: string[] = [];
  for (const [, name, value] of stripComments(css).matchAll(/(?:^|[;{\s])(transition(?:-property)?)\s*:\s*([^;}]+)/g)) {
    for (const part of splitTopLevel(value)) {
      const first = part.split(/\s+/)[0];
      if (name === 'transition' && first === 'none') continue;
      props.push(first);
    }
  }
  return props;
}

/** Properties declared inside every `@keyframes` block. */
function keyframeProperties(css: string): string[] {
  const clean = stripComments(css);
  const props: string[] = [];
  for (const match of clean.matchAll(/@keyframes\s+[\w-]+\s*\{/g)) {
    let depth = 1;
    let i = (match.index ?? 0) + match[0].length;
    const start = i;
    while (i < clean.length && depth > 0) {
      if (clean[i] === '{') depth++;
      if (clean[i] === '}') depth--;
      i++;
    }
    const body = clean.slice(start, i - 1);
    for (const [, prop] of body.matchAll(/([a-z-]+)\s*:/g)) props.push(prop);
  }
  return props;
}

describe('sign-in page styles', () => {
  const entries = Object.entries(styles);

  it('finds the CSS modules', () => {
    expect(entries.map(([path]) => path)).toEqual(
      expect.arrayContaining([
        'components/BackgroundSlideshow.module.css',
        'components/AuroraFallback.module.css',
        'components/LoginBackground.module.css',
        'components/HeroPanel.module.css',
        'components/LoginShell.module.css',
        'components/LoginCard.module.css',
        'components/FloatingField.module.css',
        'components/MotionToggle.module.css',
        'components/PhotoCaption.module.css',
        'components/SlideIndicator.module.css',
        'components/RotatingTagline.module.css',
        'components/StaggeredHeadline.module.css',
      ]),
    );
  });

  it('uses no hex colours and never brand shade 6', () => {
    const offenders = entries
      .filter(([, css]) => HEX.test(stripComments(css)) || /studrada-6\b/.test(css))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('animates and transitions only transform, opacity and filter (spec §6.2)', () => {
    const offenders = entries.flatMap(([path, css]) =>
      [...transitionedProperties(css), ...keyframeProperties(css)]
        .filter((prop) => !ANIMATABLE.has(prop))
        .map((prop) => `${path}: ${prop}`),
    );
    expect(offenders).toEqual([]);
    expect(entries.some(([, css]) => keyframeProperties(css).length > 0)).toBe(true);
    expect(entries.some(([, css]) => transitionedProperties(css).length > 0)).toBe(true);
  });

  it('crossfades photos with an opacity transition of the configured duration and ignores the pointer', () => {
    const css = styles['components/BackgroundSlideshow.module.css'];
    expect(transitionedProperties(css)).toEqual(['opacity']);
    expect(css).toMatch(/transition:\s*opacity var\(--login-crossfade\)/);
    expect(css).toMatch(/\.root\s*\{[^}]*pointer-events:\s*none/);
    expect(css).toMatch(/\.root\s*\{[^}]*position:\s*fixed/);
  });

  it('stops the aurora drift under prefers-reduced-motion', () => {
    const css = styles['components/AuroraFallback.module.css'];
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)\s*\{[^}]*animation:\s*none/);
  });

  describe('layout and hero (INC-058)', () => {
    const shell = stripComments(styles['components/LoginShell.module.css']);
    const hero = stripComments(styles['components/HeroPanel.module.css']);
    const desktop = `@media (min-width: ${loginPageConfig.layout.desktopMinWidthEm}em)`;

    /** Body of the first `@media` block with the given prelude. */
    function mediaBlock(css: string, prelude: string): string {
      const start = css.indexOf(prelude);
      if (start < 0) return '';
      let depth = 0;
      let i = css.indexOf('{', start);
      const open = i;
      for (; i < css.length; i++) {
        if (css[i] === '{') depth++;
        if (css[i] === '}' && --depth === 0) break;
      }
      return css.slice(open + 1, i);
    }

    it('is one column on phones and hero + card side by side from the configured breakpoint (HERO-1)', () => {
      const base = shell.slice(0, shell.indexOf('@media'));
      expect(base).toMatch(/\.main\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/);
      const wide = mediaBlock(shell, desktop);
      expect(wide).toMatch(/\.main\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s+minmax\(0,\s*420px\)/);
      expect(mediaBlock(hero, desktop)).not.toBe('');
    });

    it('never lets the frame overflow horizontally', () => {
      expect(shell).toMatch(/\.shell\s*\{[^}]*overflow-x:\s*clip/);
      expect(shell).toMatch(/\.cardColumn\s*\{[^}]*min-width:\s*0/);
      expect(hero).toMatch(/\.title\s*\{[^}]*overflow-wrap:\s*anywhere/);
    });

    it('colours text on the overlay only white or studrada shade 2 (CLAUDE.md, never shade 6)', () => {
      for (const css of [shell, hero]) {
        const colours = [...css.matchAll(/(?:^|[;{\s])color\s*:\s*([^;}]+)/g)].map(([, value]) => value.trim());
        expect(colours.length).toBeGreaterThan(0);
        for (const value of colours) {
          expect(['var(--mantine-color-white)', 'var(--mantine-color-studrada-2)']).toContain(value);
        }
      }
    });
  });

  describe('glass card and fields (INC-059)', () => {
    const card = stripComments(styles['components/LoginCard.module.css']);
    const field = stripComments(styles['components/FloatingField.module.css']);

    /** Body of the first block whose prelude is exactly `prelude`. */
    function block(css: string, prelude: string): string {
      const start = css.indexOf(prelude);
      if (start < 0) return '';
      let depth = 0;
      let i = css.indexOf('{', start);
      const open = i;
      for (; i < css.length; i++) {
        if (css[i] === '{') depth++;
        if (css[i] === '}' && --depth === 0) break;
      }
      return css.slice(open + 1, i);
    }

    /** Body of every keyframes block, keyed by name. */
    function keyframes(css: string): Record<string, string> {
      return Object.fromEntries(
        [...css.matchAll(/@keyframes\s+([\w-]+)/g)].map(([prelude, name]) => [name, block(css, prelude)]),
      );
    }

    it('is translucent with a backdrop blur and has a solid fallback without backdrop-filter (CARD-1)', () => {
      const base = block(card, '.card {');
      expect(base).toMatch(/backdrop-filter:\s*blur\(/);
      expect(base).toMatch(/background-color:\s*color-mix\(in srgb, var\(--mantine-color-body\) \d+%, transparent\)/);
      expect(base).toMatch(/border:\s*1px solid/);
      expect(base).toMatch(/box-shadow:\s*var\(--mantine-shadow-/);
      expect(base).toMatch(/border-radius:\s*var\(--mantine-radius-/);
      const fallback = block(card, '@supports not (backdrop-filter: blur(1px))');
      expect(fallback).toMatch(/\.card\s*\{[^}]*background-color:\s*var\(--mantine-color-body\)/);
      expect(fallback).toMatch(/dark'\]\s*\.card\s*\{[^}]*background-color:\s*var\(--mantine-color-dark-7\)/);
    });

    it('enters with opacity + transform and staggers the fields (CARD-2)', () => {
      const frames = keyframes(card);
      expect(Object.keys(frames)).toEqual(
        expect.arrayContaining(['loginCardEnter', 'loginItemEnter', 'loginFadeIn', 'loginShakeA', 'loginShakeB']),
      );
      expect(frames.loginCardEnter).toMatch(/transform:\s*translateY\(var\(--login-card-rise\)\)/);
      expect(block(card, '.item {')).toMatch(/animation-delay:[^;]*var\(--login-item[^;]*var\(--login-field-stagger\)/);
    });

    it('fades the error with opacity only and shakes with transform only (CARD-7)', () => {
      const frames = keyframes(card);
      expect(keyframeProperties(`@keyframes k {${frames.loginFadeIn}}`)).toEqual(['opacity', 'opacity']);
      for (const name of ['loginShakeA', 'loginShakeB']) {
        const props = new Set(keyframeProperties(`@keyframes k {${frames[name]}}`));
        expect([...props]).toEqual(['transform']);
      }
      expect(frames.loginShakeA).toBe(frames.loginShakeB);
      expect(block(card, '.shakeA {')).toMatch(/animation:\s*\S+ var\(--login-shake\)/);
      expect(block(card, '.error {')).toMatch(/animation:\s*\S+ var\(--login-error-fade\)/);
    });

    it('turns off entrance, stagger, shake, lift and shine under prefers-reduced-motion (CARD-7, §6.3)', () => {
      const reduced = block(card, '@media (prefers-reduced-motion: reduce)');
      expect(reduced).toMatch(/\.card,\s*\.item,\s*\.error,\s*\.shakeA,\s*\.shakeB\s*\{\s*animation:\s*none/);
      expect(reduced).toMatch(/\.submit,\s*\.submit::after\s*\{\s*transition:\s*none/);
      expect(reduced).toMatch(/\.submit:hover[^{]*\{\s*transform:\s*none/);
      expect(reduced).toMatch(/\.submit::after\s*\{\s*display:\s*none/);
      expect(block(field, '@media (prefers-reduced-motion: reduce)')).toMatch(/transition:\s*none/);
    });

    it('lifts and sweeps the submit button only when it is enabled (CARD-6)', () => {
      expect(card).toMatch(/\.submit:hover:not\(:disabled, \[data-loading\]\)\s*\{[^}]*transform:\s*translateY/);
      expect(card).toMatch(/\.submit:hover:not\(:disabled, \[data-loading\]\)::after\s*\{[^}]*transform:\s*translateX/);
    });

    it('floats the label on focus, value and autofill with transform only (CARD-3, CARD-4)', () => {
      expect(field).toMatch(/\.field:focus-within \.label,\s*\.field\[data-filled\] \.label\s*\{\s*transform:/);
      expect(field).toMatch(/\.input:autofill \+ \.label\s*\{\s*transform:/);
      expect(field).toMatch(/\.input:-webkit-autofill \+ \.label\s*\{\s*transform:/);
      expect(block(field, '.label {')).toMatch(/pointer-events:\s*none/);
      expect(block(field, '.underline {')).toMatch(/transform:\s*scaleX\(0\)/);
      expect(field).toMatch(/\.field:focus-within::before\s*\{\s*opacity:\s*1/);
    });

    it('keeps the Caps Lock hint on the warning scales, never brand blue (CARD-5)', () => {
      const hint = [block(card, '.capsHint {'), block(card, '.capsIcon {')].join('\n');
      expect(hint).toMatch(/--mantine-color-(yellow|orange)-/);
      expect(hint).not.toMatch(/studrada|primary|anchor/);
    });
  });

  describe('motion, captions, indicator, tagline (INC-060)', () => {
    const slideshow = stripComments(styles['components/BackgroundSlideshow.module.css']);
    const card = stripComments(styles['components/LoginCard.module.css']);
    const overlayModules = [
      'components/MotionToggle.module.css',
      'components/PhotoCaption.module.css',
      'components/SlideIndicator.module.css',
      'components/RotatingTagline.module.css',
      'components/StaggeredHeadline.module.css',
      'components/LoginShell.module.css',
    ];

    /** Body of every keyframes block, keyed by name. */
    function keyframeBodies(css: string): Record<string, string> {
      const out: Record<string, string> = {};
      for (const match of css.matchAll(/@keyframes\s+([\w-]+)\s*\{/g)) {
        let depth = 1;
        let i = (match.index ?? 0) + match[0].length;
        const start = i;
        while (i < css.length && depth > 0) {
          if (css[i] === '{') depth++;
          if (css[i] === '}') depth--;
          i++;
        }
        out[match[1]] = css.slice(start, i - 1);
      }
      return out;
    }

    const propsOf = (body: string) => [...new Set(keyframeProperties(`@keyframes k {${body}}`))].sort();

    it('runs Ken Burns as a transform-only zoom + pan of the configured length (BG-4)', () => {
      const frames = keyframeBodies(slideshow);
      expect(propsOf(frames.loginKenBurns)).toEqual(['transform']);
      expect(frames.loginKenBurns).toMatch(/scale\(var\(--login-kb-scale-from\)\)/);
      expect(frames.loginKenBurns).toMatch(/scale\(var\(--login-kb-scale-to\)\) translate\(var\(--login-kb-x\), var\(--login-kb-y\)\)/);
      expect(slideshow).toMatch(/\.kenBurns\s*\{[^}]*animation:\s*loginKenBurns var\(--login-kb-duration\)/);
      expect(slideshow).toMatch(/@media \(prefers-reduced-motion: reduce\)\s*\{\s*\.kenBurns\s*\{\s*animation:\s*none/);
    });

    it('fills the indicator with scaleX only and freezes it when not running (CAP-3)', () => {
      const css = stripComments(styles['components/SlideIndicator.module.css']);
      const frames = keyframeBodies(css);
      expect(propsOf(frames.loginIndicatorFill)).toEqual(['transform']);
      expect(frames.loginIndicatorFill).toMatch(/scaleX\(0\)[\s\S]*scaleX\(1\)/);
      expect(css).toMatch(/\.filling\[data-running='false'\]\s*\{\s*animation-play-state:\s*paused/);
      expect(css).toMatch(/\.segment:focus-visible\s*\{[^}]*outline:\s*2px solid var\(--mantine-color-studrada-2\)/);
    });

    it('animates the tagline, headline and caption with transform and opacity only (HERO-4, HERO-5, CAP-1)', () => {
      for (const path of [
        'components/RotatingTagline.module.css',
        'components/StaggeredHeadline.module.css',
        'components/PhotoCaption.module.css',
      ]) {
        const css = stripComments(styles[path]);
        for (const body of Object.values(keyframeBodies(css))) {
          for (const prop of propsOf(body)) expect(['opacity', 'transform']).toContain(prop);
        }
        expect(transitionedProperties(css).every((prop) => prop === 'opacity')).toBe(true);
      }
      // All phrases share one grid cell: the height never changes (no layout shift).
      const tagline = stripComments(styles['components/RotatingTagline.module.css']);
      expect(tagline).toMatch(/\.tagline\s*\{[^}]*display:\s*grid/);
      expect(tagline).toMatch(/\.phrase\s*\{[^}]*grid-area:\s*1 \/ 1/);
    });

    it('reserves fixed-height slots for the caption and the indicator (no layout shift)', () => {
      const shell = stripComments(styles['components/LoginShell.module.css']);
      expect(shell).toMatch(/\.captionCompact,\s*\.captionWide\s*\{[^}]*height:\s*1\.75rem/);
      expect(shell).toMatch(/\.indicatorSlot\s*\{[^}]*height:\s*1\.75rem/);
      expect(stripComments(styles['components/PhotoCaption.module.css'])).toMatch(
        /\.text\s*\{[^}]*white-space:\s*nowrap[^}]*text-overflow:\s*ellipsis/,
      );
    });

    it('turns off the card entrance, stagger and message fade when the user pauses motion (§6.3)', () => {
      expect(card).toMatch(
        /\.host\[data-motion='off'\] \.card,\s*\.host\[data-motion='off'\] \.item,\s*\.host\[data-motion='off'\] \.error\s*\{\s*animation:\s*none/,
      );
    });

    it('colours the new overlay text and icons only white or studrada shade 2, focus rings shade 2', () => {
      for (const path of overlayModules) {
        const css = stripComments(styles[path]);
        const colours = [...css.matchAll(/(?:^|[;{\s])color\s*:\s*([^;}]+)/g)].map(([, value]) => value.trim());
        for (const value of colours) {
          expect(['var(--mantine-color-white)', 'var(--mantine-color-studrada-2)']).toContain(value);
        }
        for (const [, value] of css.matchAll(/outline\s*:\s*([^;}]+)/g)) {
          expect(value).toMatch(/var\(--mantine-color-studrada-2\)/);
        }
      }
    });
  });

  describe('scanner', () => {
    it('detects forbidden properties', () => {
      expect(transitionedProperties('.a { transition: width 1s, opacity var(--x, 1s) ease; }')).toEqual([
        'width',
        'opacity',
      ]);
      expect(transitionedProperties('.a { transition-property: top, transform; }')).toEqual(['top', 'transform']);
      expect(keyframeProperties('@keyframes k { from { left: 0; } to { transform: none; opacity: 1; } }')).toEqual([
        'left',
        'transform',
        'opacity',
      ]);
      expect(
        keyframeProperties('@keyframes k { 50% { background-position: 0 0; } }').includes('background-position'),
      ).toBe(true);
    });
  });
});
