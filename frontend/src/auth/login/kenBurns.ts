import { loginPageConfig, type KenBurnsDirection } from './loginPage.config';

/**
 * Ken Burns pan direction of a newly visible slide (MAIN_PAGE_FEATURE.md BG-4): a random preset index that is never
 * the one used by the previous slide, so consecutive slides never drift the same way.
 *
 * @param previous preset index of the previous slide, or null for the first slide
 * @param count number of presets
 * @param random random source in [0, 1), injectable for tests
 */
export function pickKenBurnsDirection(previous: number | null, count: number, random: () => number = Math.random): number {
  if (count <= 1) return 0;
  const excluded = previous !== null && previous >= 0 && previous < count;
  const choices = excluded ? count - 1 : count;
  const r = Math.min(Math.max(random(), 0), 0.999999999);
  const pick = Math.floor(r * choices);
  // Skip over the excluded preset: indices from `previous` on shift up by one.
  return excluded && pick >= previous ? pick + 1 : pick;
}

/** CSS custom properties of one Ken Burns run with the given preset. */
export function kenBurnsVars(direction: KenBurnsDirection): Record<string, string> {
  const { panPercent } = loginPageConfig.kenBurns;
  return {
    '--login-kb-x': `${direction.x * panPercent}%`,
    '--login-kb-y': `${direction.y * panPercent}%`,
  };
}
