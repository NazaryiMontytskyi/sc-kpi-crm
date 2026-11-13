import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { useMediaQueryMatch } from '../hooks/useMediaQueryMatch';
import type { Slideshow } from '../hooks/useSlideshow';
import { DESKTOP_QUERY } from '../layoutQueries';
import type { SlideImage } from '../slideshowImages';
import { PhotoCaption } from './PhotoCaption';
import { SlideIndicator } from './SlideIndicator';
import { useSlideControlsHosts } from './slideControlsHosts';

interface SlideControlsProps {
  show: Slideshow;
  slides: readonly SlideImage[];
  /** Localized caption of a photo, undefined when it has none (CAP-2; `captionFor` over captions.json). */
  captionOf: (slideId: string, language: string) => string | undefined;
  /** `prefers-reduced-motion`: the indicator is static. */
  prefersReducedMotion: boolean;
  /** Reduced motion or paused: the caption only fades. */
  motionReduced: boolean;
}

/**
 * Caption (CAP-1, CAP-2) and indicator (CAP-3, CAP-4) of the background slideshow, rendered through portals into the
 * slots LoginShell provides. Nothing is rendered outside a LoginShell.
 */
export function SlideControls({ show, slides, captionOf, prefersReducedMotion, motionReduced }: SlideControlsProps) {
  const { i18n } = useTranslation();
  const hosts = useSlideControlsHosts();
  const desktop = useMediaQueryMatch(DESKTOP_QUERY);
  const captionHost = desktop ? hosts.captionWide : hosts.captionCompact;

  const slide = show.current !== null && show.currentLoaded ? slides[show.current] : undefined;
  const caption = slide ? captionOf(slide.id, i18n.language) : undefined;

  return (
    <>
      {captionHost &&
        slide &&
        createPortal(<PhotoCaption text={caption} slideKey={slide.id} motionReduced={motionReduced} />, captionHost)}
      {hosts.indicator &&
        show.current !== null &&
        createPortal(
          <SlideIndicator
            count={show.order.length}
            active={show.order.indexOf(show.current)}
            progressKey={show.progressKey}
            durationMs={show.intervalMs}
            running={show.running}
            mode={prefersReducedMotion ? 'static' : 'animate'}
            onSelect={(position) => {
              const target = show.order[position];
              if (target !== undefined) show.goTo(target);
            }}
          />,
          hosts.indicator,
        )}
    </>
  );
}
