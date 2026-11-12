import { usePrefersReducedMotion } from '../hooks/usePrefersReducedMotion';
import { discoverSlides } from '../slideshowImages';
import { BackgroundSlideshow } from './BackgroundSlideshow';

/**
 * Entry of the lazily loaded background chunk (see LoginBackground.tsx). Importing slideshowImages here, and only
 * here, keeps the photo glob and picture metadata out of the entry chunk (ADR-0009, 30 KB budget).
 */
const slides = discoverSlides();

export default function BackgroundLayer() {
  const reducedMotion = usePrefersReducedMotion();
  return <BackgroundSlideshow slides={slides} reducedMotion={reducedMotion} />;
}
