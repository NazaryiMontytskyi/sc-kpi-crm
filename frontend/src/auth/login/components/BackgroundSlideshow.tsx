import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import { useSlideshow, type Slideshow } from '../hooks/useSlideshow';
import { kenBurnsVars, pickKenBurnsDirection } from '../kenBurns';
import { loginPageConfig } from '../loginPage.config';
import { pictureSources, SLIDE_SIZES } from '../slidePicture';
import type { SlideImage } from '../slideshowImages';
import { AuroraFallback } from './AuroraFallback';
import classes from './BackgroundSlideshow.module.css';

/** Pseudo slide number of the aurora fallback in the crossfade bookkeeping. */
const AURORA = -1;

export interface BackgroundSlideshowProps {
  slides: readonly SlideImage[];
  paused?: boolean;
  reducedMotion?: boolean;
  /** Random source of the shuffle (tests). */
  random?: () => number;
  /** Random source of the Ken Burns direction picker (tests). */
  kenBurnsRandom?: () => number;
  /**
   * Interactive parts that follow the slideshow state (caption, indicator, INC-060). Rendered outside the
   * decorative, aria-hidden layer.
   */
  controls?: (show: Slideshow) => ReactNode;
}

type LayerState = 'visible' | 'outgoing' | 'pending';

const { crossfadeMs, slideDurationMs } = loginPageConfig.slideshow;
const { scaleFrom, scaleTo, directions } = loginPageConfig.kenBurns;
const { topOpacity, bottomOpacity, vignetteOpacity, grainOpacity } = loginPageConfig.overlay;

const percent = (value: number) => `${Math.round(value * 100)}%`;
const rootStyle = {
  '--login-crossfade': `${crossfadeMs}ms`,
  '--login-overlay-top': percent(topOpacity),
  '--login-overlay-bottom': percent(bottomOpacity),
  '--login-vignette': percent(vignetteOpacity),
  '--login-grain': String(grainOpacity),
  // BG-4: the zoom/pan runs through the whole time a slide is on screen, including its fade-out.
  '--login-kb-duration': `${slideDurationMs + crossfadeMs}ms`,
  '--login-kb-scale-from': String(scaleFrom),
  '--login-kb-scale-to': String(scaleTo),
} as CSSProperties;

/**
 * Full-viewport photo background of the sign-in page (BG-1, BG-3, BG-6..BG-10). Two photo layers at a time (the
 * visible slide and the preloading next one, plus the outgoing one during a crossfade), an opacity crossfade, the
 * dark overlay with vignette and film grain, and the aurora fallback until the first photo is ready. Decorative
 * only: hidden from assistive technology, never focusable, ignores the pointer.
 */
export function BackgroundSlideshow({
  slides,
  paused = false,
  reducedMotion = false,
  random,
  kenBurnsRandom,
  controls,
}: BackgroundSlideshowProps) {
  const show = useSlideshow(slides, { paused, reducedMotion, random });
  const visible = show.current !== null && show.currentLoaded ? show.current : AURORA;
  // BG-4 / §6.3: no Ken Burns under reduced motion or the user's pause.
  const kenBurns = !paused && !reducedMotion;

  // Keep the previously visible slide (or the aurora) underneath until the new one has faded in.
  const [displayed, setDisplayed] = useState(visible);
  const [outgoing, setOutgoing] = useState<number | null>(null);
  // Ken Burns preset per slide on screen; a newly visible slide never reuses the previous slide's preset.
  const [direction, setDirection] = useState<{ last: number | null; bySlide: Readonly<Record<number, number>> }>(
    () => ({ last: null, bySlide: {} }),
  );
  if (displayed !== visible) {
    setDisplayed(visible);
    setOutgoing(displayed);
    if (visible !== AURORA) {
      const picked = pickKenBurnsDirection(direction.last, directions.length, kenBurnsRandom);
      setDirection({ last: picked, bySlide: { ...direction.bySlide, [visible]: picked } });
    }
  }
  useEffect(() => {
    if (outgoing === null) return;
    const timer = setTimeout(() => setOutgoing(null), crossfadeMs);
    return () => clearTimeout(timer);
  }, [outgoing, displayed]);

  const mounted = new Set<number>();
  for (const slide of [outgoing, show.current, show.next]) {
    if (slide !== null && slide !== AURORA) mounted.add(slide);
  }
  // Stable DOM order (by slide number) so no layer is moved while it transitions; stacking comes from data-state.
  const layers = [...mounted].sort((a, b) => a - b);
  const stateOf = (slide: number): LayerState =>
    slide === visible ? 'visible' : slide === outgoing ? 'outgoing' : 'pending';

  const kenBurnsOf = (slide: number) => {
    const preset = direction.bySlide[slide];
    return kenBurns && slide !== AURORA && (slide === visible || slide === outgoing) && preset !== undefined
      ? preset
      : undefined;
  };

  return (
    <>
    <div className={classes.root} style={rootStyle} aria-hidden="true" data-testid="login-background">
      {(visible === AURORA || outgoing === AURORA) && <AuroraFallback />}
      {layers.map((index) => {
        const slide = slides[index];
        const fallbackSrcSet = slide.srcSets.webp;
        const preset = kenBurnsOf(index);
        return (
          <div key={slide.id} className={classes.layer} data-state={stateOf(index)}>
            <picture
              className={preset === undefined ? classes.picture : `${classes.picture} ${classes.kenBurns}`}
              style={preset === undefined ? undefined : (kenBurnsVars(directions[preset]) as CSSProperties)}
              data-ken-burns={preset}
            >
              {pictureSources(slide).map((source) => (
                <source
                  key={`${source.type}${source.media ?? ''}`}
                  type={source.type}
                  media={source.media}
                  srcSet={source.srcSet}
                  sizes={source.sizes}
                />
              ))}
              <img
                className={classes.image}
                src={slide.fallbackSrc}
                srcSet={fallbackSrcSet}
                sizes={fallbackSrcSet ? SLIDE_SIZES : undefined}
                alt=""
                aria-hidden="true"
                decoding="async"
                draggable={false}
                onLoad={() => show.markLoaded(index)}
                onError={() => show.markFailed(index)}
              />
            </picture>
          </div>
        );
      })}
      <div className={classes.overlay} />
      <div className={classes.grain} />
    </div>
    {controls?.(show)}
    </>
  );
}
