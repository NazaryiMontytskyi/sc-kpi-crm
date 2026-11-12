import { act, fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { changeLanguage } from '../../../i18n';
import { renderWithProviders } from '../../../test/render';
import { resetMotionPauseMemory, useMotionPreference } from '../hooks/useMotionPreference';
import { loginPageConfig } from '../loginPage.config';
import { captionFor, type SlideCaption, type SlideImage } from '../slideshowImages';
import { stubViewport } from '../testing/viewportStub';
import { BackgroundSlideshow } from './BackgroundSlideshow';
import { LoginShell } from './LoginShell';
import { SlideControls } from './SlideControls';

function slide(n: number, ext = 'jpg'): SlideImage {
  return {
    id: `image_${n}.${ext}`,
    srcSets: { webp: `/assets/image_${n}-768.webp 768w` },
    fallbackSrc: `/assets/image_${n}.webp`,
    width: 768,
    height: 512,
  };
}

const SLIDES = [slide(1), slide(2), slide(3, 'webp')];
const CAPTIONS: Record<string, SlideCaption> = {
  'image_1.jpg': { uk: 'Головний корпус', en: 'Main Building' },
  'image_2.jpg': { uk: 'Корпус № 18', en: 'Building 18' },
};
const noShuffle = () => 0.999999;

/** The background as BackgroundLayer composes it, with injected slides and captions. */
function Background({
  slides = SLIDES,
  captions = CAPTIONS,
}: {
  slides?: SlideImage[];
  /** null: no captions.json at all. */
  captions?: Record<string, SlideCaption> | null;
}) {
  const { prefersReducedMotion, paused, motionReduced } = useMotionPreference();
  return (
    <BackgroundSlideshow
      slides={slides}
      paused={paused}
      reducedMotion={prefersReducedMotion}
      random={noShuffle}
      controls={(show) => (
        <SlideControls
          show={show}
          slides={slides}
          captionOf={(id, language) => captionFor(id, language, captions ?? undefined)}
          prefersReducedMotion={prefersReducedMotion}
          motionReduced={motionReduced}
        />
      )}
    />
  );
}

function renderPage(background = <Background />) {
  return renderWithProviders(
    <LoginShell background={background}>
      <button type="submit">submit</button>
    </LoginShell>,
  );
}

function loadImages() {
  for (const img of document.querySelectorAll('img')) fireEvent.load(img);
}

describe('slide caption and indicator in the sign-in frame (CAP-1..4)', () => {
  let viewport: ReturnType<typeof stubViewport>;
  beforeEach(async () => {
    viewport = stubViewport(1280);
    localStorage.removeItem(loginPageConfig.motion.pauseStorageKey);
    resetMotionPauseMemory();
    await changeLanguage('uk');
  });
  afterEach(() => {
    viewport.restore();
    localStorage.removeItem(loginPageConfig.motion.pauseStorageKey);
  });

  it('shows the pin and the localized caption bottom-left on desktop and updates it on a language switch', async () => {
    renderPage();
    expect(screen.queryByTestId('login-photo-caption')).toBeNull();
    act(() => loadImages());
    const wide = screen.getByTestId('login-caption-slot-wide');
    const caption = within(wide).getByTestId('login-photo-caption');
    expect(caption).toHaveTextContent('Головний корпус');
    expect(caption.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByTestId('login-caption-slot-compact')).toBeEmptyDOMElement();
    expect(caption).not.toHaveAttribute('aria-live');

    await act(() => changeLanguage('en'));
    expect(within(wide).getByTestId('login-photo-caption')).toHaveTextContent('Main Building');
    await act(() => changeLanguage('uk'));
  });

  it('puts the caption at the top on phones', () => {
    viewport.resize(375);
    renderPage();
    act(() => loadImages());
    expect(within(screen.getByTestId('login-caption-slot-compact')).getByTestId('login-photo-caption')).toHaveTextContent(
      'Головний корпус',
    );
    expect(screen.getByTestId('login-caption-slot-wide')).toBeEmptyDOMElement();
    // The compact slot sits in the top bar, next to the language switch.
    expect(screen.getByTestId('login-caption-slot-compact').closest('header')).not.toBeNull();
  });

  it('renders nothing for a photo without a caption entry and never shows a file name (CAP-2)', async () => {
    renderPage(<Background slides={[slide(3, 'webp'), slide(1)]} />);
    act(() => loadImages());
    expect(screen.queryByTestId('login-photo-caption')).toBeNull();
    expect(document.body.textContent).not.toMatch(/image_\d/);
    await act(() => changeLanguage('en'));
    expect(screen.queryByTestId('login-photo-caption')).toBeNull();
    expect(document.body.textContent).not.toMatch(/image_\d/);
    await act(() => changeLanguage('uk'));
  });

  it('renders nothing without captions.json', () => {
    renderPage(<Background captions={null} />);
    act(() => loadImages());
    expect(screen.queryByTestId('login-photo-caption')).toBeNull();
    expect(document.body.textContent).not.toMatch(/image_\d/);
  });

  it('shows one segment per photo with the current one active, and jumps to the chosen photo (CAP-4)', async () => {
    const user = userEvent.setup();
    renderPage();
    act(() => loadImages());
    const slot = screen.getByTestId('login-indicator-slot');
    const segments = within(slot).getAllByRole('button');
    expect(segments).toHaveLength(3);
    expect(segments[0]).toHaveAttribute('aria-current', 'true');
    const fillBefore = within(slot).getByTestId('login-indicator-fill');

    await user.click(segments[2]);
    act(() => loadImages());
    expect(within(slot).getAllByRole('button')[2]).toHaveAttribute('aria-current', 'true');
    expect(screen.queryByTestId('login-photo-caption')).toBeNull();
    expect(within(slot).getByTestId('login-indicator-fill')).not.toBe(fillBefore);
    // Photo 3 is on screen now.
    expect(document.querySelector('[data-state="visible"] img')).toHaveAttribute('src', '/assets/image_3.webp');

    // Enter on segment 1 jumps back to photo 1 and restarts its progress.
    within(slot).getAllByRole('button')[0].focus();
    await user.keyboard('{Enter}');
    expect(within(slot).getAllByRole('button')[0]).toHaveAttribute('aria-current', 'true');
    expect(document.querySelector('[data-state="visible"] img')).toHaveAttribute('src', '/assets/image_1.webp');
  });

  it('freezes the fill and the slideshow when the user pauses, and resumes on play (FT-3, CAP-3)', async () => {
    const user = userEvent.setup();
    renderPage();
    act(() => loadImages());
    const toggle = screen.getByTestId('login-motion-toggle');
    expect(screen.getByTestId('login-indicator-fill')).toHaveAttribute('data-running', 'true');
    expect(document.querySelectorAll('[class*="kenBurns"]').length).toBeGreaterThan(0);

    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('login-indicator-fill')).toHaveAttribute('data-running', 'false');
    expect(document.querySelectorAll('[class*="kenBurns"]')).toHaveLength(0);
    expect(localStorage.getItem(loginPageConfig.motion.pauseStorageKey)).toBe('true');

    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByTestId('login-indicator-fill')).toHaveAttribute('data-running', 'true');
  });

  it('keeps the motion toggle the last focusable element, after the card and the indicator (FT-3, spec §7)', () => {
    renderPage();
    act(() => loadImages());
    const focusable = [...document.querySelectorAll<HTMLElement>('button, input, a[href], [tabindex]')].filter(
      (el) => !el.hasAttribute('disabled') && el.tabIndex >= 0,
    );
    expect(focusable.at(-1)).toBe(screen.getByTestId('login-motion-toggle'));
    const submit = screen.getByRole('button', { name: 'submit' });
    const firstSegment = within(screen.getByTestId('login-indicator-slot')).getAllByRole('button')[0];
    expect(submit.compareDocumentPosition(firstSegment) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('renders no controls outside the sign-in frame', () => {
    renderWithProviders(<Background />);
    act(() => loadImages());
    expect(screen.queryByTestId('login-indicator')).toBeNull();
    expect(screen.queryByTestId('login-photo-caption')).toBeNull();
  });
});
