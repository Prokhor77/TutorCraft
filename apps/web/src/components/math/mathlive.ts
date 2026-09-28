/**
 * Lazy MathLive loader (~250 KB gzip, only for editors). Configured once:
 * - fonts: KaTeX fonts are already served by `katex.min.css` (same families), so MathLive does not
 *   fetch its own copy (`fontsDirectory = null`);
 * - sounds: off;
 * - Russian school notation shortcuts (tg, ctg, arctg…) on top of the MathLive defaults.
 */
type MathLiveModule = typeof import('mathlive');

export const RUSSIAN_INLINE_SHORTCUTS: Readonly<Record<string, string>> = {
  tg: '\\operatorname{tg}',
  ctg: '\\operatorname{ctg}',
  arctg: '\\operatorname{arctg}',
  arcctg: '\\operatorname{arcctg}',
  lg: '\\lg',
};

let mathLivePromise: Promise<MathLiveModule> | null = null;

function configure(module: MathLiveModule): MathLiveModule {
  module.MathfieldElement.fontsDirectory = null;
  module.MathfieldElement.soundsDirectory = null;
  return module;
}

export function loadMathLive(): Promise<MathLiveModule> {
  mathLivePromise ??= import('mathlive').then(configure);
  mathLivePromise.catch(() => {
    mathLivePromise = null;
  });
  return mathLivePromise;
}
