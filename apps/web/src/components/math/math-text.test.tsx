import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MathText } from './math-text';

describe('MathText', () => {
  it('renders text, escaped dollars and inline formulas', async () => {
    const { container } = render(<MathText value={'Цена \\$5, площадь $\\pi r^2$'} />);
    expect(container.textContent).toContain('Цена $5, площадь');
    expect(await screen.findByRole('math', { name: '\\pi r^2' })).toBeInTheDocument();
  });
});
