import { describe, expect, it } from 'vitest';
import { captionFor, discoverSlides, loadCaptions, parseCaptions } from './slideshowImages';
import source from './slideshowImages.ts?raw';

const DIR = '../../../../assets/main_page/images/';

function picture(name: string, width = 1920, height = 1080) {
  return {
    sources: {
      avif: `/assets/${name}-768.avif 768w, /assets/${name}-1280.avif 1280w, /assets/${name}-1920.avif 1920w`,
      webp: `/assets/${name}-768.webp 768w, /assets/${name}-1280.webp 1280w, /assets/${name}-1920.webp 1920w`,
    },
    img: { src: `/assets/${name}-1920.webp`, w: width, h: height },
  };
}

describe('discoverSlides', () => {
  it('globs every supported extension from assets/main_page/images', () => {
    const glob = source.match(/import\.meta\.glob\(\s*'([^']+)'/)?.[1] ?? '';
    expect(glob.startsWith(DIR)).toBe(true);
    for (const ext of ['jpg', 'jpeg', 'jfif', 'png', 'webp', 'avif']) {
      expect(glob).toMatch(new RegExp(`[{,]${ext}[,}]`));
    }
    expect(source).toContain("w: '768;1280;1920'");
    expect(source).toContain("format: 'avif;webp'");
  });

  it('returns [] for an empty folder', () => {
    expect(discoverSlides({})).toEqual([]);
  });

  it('maps a processed picture to a slide keyed by file name', () => {
    expect(discoverSlides({ [`${DIR}image_1.jpg`]: picture('image_1') })).toEqual([
      {
        id: 'image_1.jpg',
        srcSets: picture('image_1').sources,
        fallbackSrc: '/assets/image_1-1920.webp',
        width: 1920,
        height: 1080,
      },
    ]);
  });

  it('picks up a newly added file with no code change', () => {
    const before = { [`${DIR}image_1.jpg`]: picture('image_1') };
    const after = { ...before, [`${DIR}new-photo.jfif`]: picture('new-photo') };
    expect(discoverSlides(before).map((s) => s.id)).toEqual(['image_1.jpg']);
    expect(discoverSlides(after).map((s) => s.id)).toEqual(['image_1.jpg', 'new-photo.jfif']);
  });

  it('orders slides by file name with numeric awareness', () => {
    const modules = Object.fromEntries(
      ['image_10.jpg', 'image_2.png', 'image_1.jpg'].map((n) => [`${DIR}${n}`, picture(n)]),
    );
    expect(discoverSlides(modules).map((s) => s.id)).toEqual(['image_1.jpg', 'image_2.png', 'image_10.jpg']);
  });

  it('skips entries that are not processed pictures', () => {
    const modules = {
      [`${DIR}a.jpg`]: '/assets/a.jpg',
      [`${DIR}b.jpg`]: null,
      [`${DIR}c.jpg`]: { sources: {}, img: { src: '' } },
      [`${DIR}d.jpg`]: picture('d'),
    };
    expect(discoverSlides(modules).map((s) => s.id)).toEqual(['d.jpg']);
  });

  it('builds a webp srcset for a photo narrower than the smallest width', () => {
    const narrow = { sources: { avif: '/assets/small.avif 678w' }, img: { src: '/assets/small.webp', w: 678, h: 452 } };
    const [slide] = discoverSlides({ [`${DIR}small.jfif`]: narrow });
    expect(slide.srcSets).toEqual({ avif: '/assets/small.avif 678w', webp: '/assets/small.webp 678w' });
  });

  it('stays safe on the real folder, where unit tests do not encode images', () => {
    expect(() => discoverSlides()).not.toThrow();
  });
});

describe('captionFor', () => {
  const captions = {
    'image_7.jpg': { uk: 'Головний корпус, вечір', en: 'Main Building, evening' },
    'image_9.webp': { uk: 'Тільки українською' },
    'image_2.png': { uk: 'image_2.png', en: 'photo.jpg' },
    'image_3.png': 'not an object',
    'image_4.webp': { uk: '   ', en: 42 },
  };

  it('returns the localized caption', () => {
    expect(captionFor('image_7.jpg', 'uk', captions)).toBe('Головний корпус, вечір');
    expect(captionFor('image_7.jpg', 'en', captions)).toBe('Main Building, evening');
    expect(captionFor('image_7.jpg', 'en-US', captions)).toBe('Main Building, evening');
  });

  it('returns undefined when captions are missing or malformed', () => {
    expect(captionFor('image_7.jpg', 'uk', undefined)).toBeUndefined();
    expect(captionFor('image_7.jpg', 'uk', null)).toBeUndefined();
    expect(captionFor('image_7.jpg', 'uk', '{"image_7.jpg":{"uk":"x"}}')).toBeUndefined();
    expect(captionFor('image_7.jpg', 'uk', [captions])).toBeUndefined();
  });

  it('returns undefined when the image has no entry or no text in that language', () => {
    expect(captionFor('image_1.jpg', 'uk', captions)).toBeUndefined();
    expect(captionFor('image_9.webp', 'en', captions)).toBeUndefined();
    expect(captionFor('image_3.png', 'uk', captions)).toBeUndefined();
    expect(captionFor('image_4.webp', 'uk', captions)).toBeUndefined();
    expect(captionFor('image_4.webp', 'en', captions)).toBeUndefined();
    expect(captionFor('toString', 'uk', captions)).toBeUndefined();
  });

  it('never returns a file name', () => {
    expect(captionFor('image_2.png', 'uk', captions)).toBeUndefined();
    expect(captionFor('image_2.png', 'en', captions)).toBeUndefined();
  });
});

describe('captions.json loading', () => {
  it('parses a valid file and rejects missing or malformed ones', () => {
    expect(parseCaptions('{"a.jpg":{"uk":"А","en":"A"}}')).toEqual({ 'a.jpg': { uk: 'А', en: 'A' } });
    expect(parseCaptions(undefined)).toBeUndefined();
    expect(parseCaptions('{ not json')).toBeUndefined();
    expect(parseCaptions('[1,2]')).toBeUndefined();
    expect(parseCaptions('"text"')).toBeUndefined();
    expect(loadCaptions({})).toBeUndefined();
    expect(loadCaptions({ [`${DIR}captions.json`]: '{ broken' })).toBeUndefined();
  });

  it('reads the committed captions.json keyed by existing file names', () => {
    const captions = loadCaptions();
    expect(captions).toBeDefined();
    expect(captionFor('image_7.jpg', 'uk', captions)).toBe('Головний корпус, вечір');
    expect(captionFor('image_7.jpg', 'en', captions)).toBe('Main Building, evening');
    expect(captionFor('image_2.png', 'uk', captions)).toBeUndefined();
  });
});
