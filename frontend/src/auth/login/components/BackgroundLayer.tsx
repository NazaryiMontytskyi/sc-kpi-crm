import { useMotionPreference } from '../hooks/useMotionPreference';
import { captionFor, discoverSlides, loadCaptions } from '../slideshowImages';
import { BackgroundSlideshow } from './BackgroundSlideshow';
import { SlideControls } from './SlideControls';

/**
 * Entry of the lazily loaded background chunk (see LoginBackground.tsx). Importing slideshowImages here, and only
 * here, keeps the photo glob, picture metadata and captions out of the entry chunk (ADR-0009, 30 KB budget).
 */
const slides = discoverSlides();
const captions = loadCaptions();
const captionOf = (slideId: string, language: string) => captionFor(slideId, language, captions);

export default function BackgroundLayer() {
  const { prefersReducedMotion, paused, motionReduced } = useMotionPreference();
  return (
    <BackgroundSlideshow
      slides={slides}
      paused={paused}
      reducedMotion={prefersReducedMotion}
      controls={(show) => (
        <SlideControls
          show={show}
          slides={slides}
          captionOf={captionOf}
          prefersReducedMotion={prefersReducedMotion}
          motionReduced={motionReduced}
        />
      )}
    />
  );
}
