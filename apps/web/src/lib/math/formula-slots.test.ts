import { describe, expect, it } from 'vitest';
import {
  fieldToSource,
  findSlot,
  sourceToField,
  stripSlots,
  templateToSource,
} from './formula-slots';

describe('formula slots', () => {
  it('turns every template placeholder into a visible slot', () => {
    expect(templateToSource('\\frac{#0}{#?}')).toBe('\\frac{□}{□}');
    expect(templateToSource('\\int_{#?}^{#?}#0\\,d#?')).toBe('\\int_{□}^{□}□\\,d□');
  });

  it('wraps the selected text with #0', () => {
    expect(templateToSource('\\sqrt{#0}', 'x+1')).toBe('\\sqrt{x+1}');
    expect(templateToSource('#0^{#?}', 'a')).toBe('a^{□}');
  });

  it('round-trips slots through the MathLive field', () => {
    const source = '\\frac{□}{2}+□';
    expect(sourceToField(source)).toBe('\\frac{\\placeholder{}}{2}+\\placeholder{}');
    expect(fieldToSource(sourceToField(source))).toBe(source);
    expect(fieldToSource('\\placeholder[a]{}')).toBe('□');
  });

  it('shows hand-typed empty arguments as slots in the visual field', () => {
    expect(sourceToField('\\frac{}{}')).toBe('\\frac{\\placeholder{}}{\\placeholder{}}');
    expect(sourceToField('x^{} + \\sqrt{ }')).toBe('x^{\\placeholder{}} + \\sqrt{\\placeholder{}}');
    expect(sourceToField('{}^{14}C')).toBe('{}^{14}C');
  });

  it('removes unfilled slots before saving', () => {
    expect(stripSlots('\\frac{x}{□}')).toBe('\\frac{x}{}');
  });

  it('finds the next and previous slot with wrap-around', () => {
    const source = 'a□b□';
    expect(findSlot(source, 0, 'forward')).toBe(1);
    expect(findSlot(source, 2, 'forward')).toBe(3);
    expect(findSlot(source, 4, 'forward')).toBe(1);
    expect(findSlot(source, 3, 'backward')).toBe(1);
    expect(findSlot(source, 1, 'backward')).toBe(3);
    expect(findSlot('abc', 0, 'forward')).toBe(-1);
  });
});
