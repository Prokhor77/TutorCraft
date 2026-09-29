/**
 * Formula palette (templates with MathLive placeholders) and a library of ready formulas.
 *
 * Placeholder syntax (MathLive): `#0` — the current selection or the first slot, `#?` — an empty slot.
 * Labels are content data (not UI chrome), so they live here instead of messages/*.json.
 */
import { templateToSource } from './formula-slots';

export type LocalizedText = Readonly<{ ru: string; en: string }>;

export type FormulaEntry = Readonly<{ latex: string; label: LocalizedText; keywords?: string }>;
export type FormulaGroup = Readonly<{
  id: string;
  title: LocalizedText;
  items: readonly FormulaEntry[];
}>;

type EntryTuple = readonly [latex: string, ru: string, en: string, keywords?: string];

function group(id: string, ru: string, en: string, tuples: readonly EntryTuple[]): FormulaGroup {
  return {
    id,
    title: { ru, en },
    items: tuples.map(([latex, entryRu, entryEn, keywords]) => ({
      latex,
      label: { ru: entryRu, en: entryEn },
      ...(keywords ? { keywords } : {}),
    })),
  };
}

export function localize(text: LocalizedText, locale: string): string {
  return locale === 'ru' ? text.ru : text.en;
}

/** KaTeX cannot render MathLive placeholders — show an empty slot box instead. */
export function toPreviewLatex(template: string): string {
  return templateToSource(template);
}

// ─── Palette: building blocks ────────────────────────────────────────────────

export const PALETTE_GROUPS: readonly FormulaGroup[] = [
  group('basic', 'Основное', 'Basic', [
    ['\\frac{#0}{#?}', 'Дробь', 'Fraction', 'деление divide'],
    ['#0^{#?}', 'Степень', 'Power', 'показатель exponent квадрат'],
    ['#0_{#?}', 'Нижний индекс', 'Subscript', 'индекс index'],
    ['#0_{#?}^{#?}', 'Индекс и степень', 'Sub- and superscript'],
    ['\\sqrt{#0}', 'Квадратный корень', 'Square root', 'корень root'],
    ['\\sqrt[#?]{#0}', 'Корень n-й степени', 'n-th root', 'корень root кубический'],
    ['\\left(#0\\right)', 'Круглые скобки', 'Parentheses', 'скобки'],
    ['\\left[#0\\right]', 'Квадратные скобки', 'Brackets', 'скобки'],
    ['\\left\\lbrace #0\\right\\rbrace', 'Фигурные скобки', 'Braces', 'скобки'],
    ['\\left|#0\\right|', 'Модуль', 'Absolute value', 'abs модуль'],
    ['\\left\\|#0\\right\\|', 'Норма', 'Norm', 'длина вектора'],
    ['\\lfloor #0\\rfloor', 'Целая часть (пол)', 'Floor', 'антье'],
    ['\\lceil #0\\rceil', 'Потолок', 'Ceiling'],
    ['#0^{\\circ}', 'Градусы', 'Degrees', 'угол градус'],
    ['#0\\%', 'Проценты', 'Percent', 'процент'],
    ['#0!', 'Факториал', 'Factorial'],
    ['\\pm', 'Плюс-минус', 'Plus-minus'],
    ['\\mp', 'Минус-плюс', 'Minus-plus'],
    ['\\times', 'Умножение (крест)', 'Times', 'умножить'],
    ['\\cdot', 'Умножение (точка)', 'Dot', 'умножить'],
    ['\\div', 'Деление', 'Division'],
    ['\\infty', 'Бесконечность', 'Infinity'],
    ['\\ldots', 'Многоточие', 'Ellipsis', 'точки'],
    ['\\cdots', 'Многоточие по центру', 'Centered dots'],
    ['\\text{#0}', 'Текст в формуле', 'Text', 'слово'],
  ]),
  group('accents', 'Надстрочные', 'Accents', [
    ['\\vec{#0}', 'Вектор', 'Vector', 'стрелка'],
    ['\\overrightarrow{#0}', 'Вектор (длинный)', 'Long vector', 'AB стрелка'],
    ['\\overline{#0}', 'Черта сверху', 'Overline', 'отрезок период сопряжение'],
    ['\\underline{#0}', 'Черта снизу', 'Underline'],
    ['\\hat{#0}', 'Крышка', 'Hat'],
    ['\\bar{#0}', 'Черта', 'Bar', 'среднее'],
    ['\\tilde{#0}', 'Тильда', 'Tilde'],
    ['\\dot{#0}', 'Точка сверху', 'Dot accent', 'производная по времени'],
    ['\\ddot{#0}', 'Две точки', 'Double dot'],
    ['\\overset{\\frown}{#0}', 'Дуга', 'Arc', 'дуга окружности'],
    ['\\underbrace{#0}_{#?}', 'Фигурная скобка снизу', 'Underbrace'],
    ['\\overbrace{#0}^{#?}', 'Фигурная скобка сверху', 'Overbrace'],
    ['\\overset{#?}{#0}', 'Надпись сверху', 'Overset'],
    ['\\underset{#?}{#0}', 'Надпись снизу', 'Underset'],
  ]),
  group('relations', 'Отношения', 'Relations', [
    ['=', 'Равно', 'Equals'],
    ['\\neq', 'Не равно', 'Not equal'],
    ['\\approx', 'Приближённо', 'Approximately', 'примерно'],
    ['\\equiv', 'Тождественно', 'Identical', 'сравнимо'],
    ['<', 'Меньше', 'Less than'],
    ['>', 'Больше', 'Greater than'],
    ['\\leq', 'Меньше или равно', 'Less or equal', 'le'],
    ['\\geq', 'Больше или равно', 'Greater or equal', 'ge'],
    ['\\ll', 'Много меньше', 'Much less'],
    ['\\gg', 'Много больше', 'Much greater'],
    ['\\sim', 'Подобно', 'Similar', 'эквивалентно'],
    ['\\cong', 'Равно (конгруэнтно)', 'Congruent'],
    ['\\propto', 'Пропорционально', 'Proportional'],
    ['\\parallel', 'Параллельно', 'Parallel'],
    ['\\perp', 'Перпендикулярно', 'Perpendicular'],
    ['\\mid', 'Делит', 'Divides'],
    ['\\coloneqq', 'По определению', 'Defined as'],
  ]),
  group('greek', 'Греческие', 'Greek', [
    ['\\alpha', 'альфа', 'alpha'],
    ['\\beta', 'бета', 'beta'],
    ['\\gamma', 'гамма', 'gamma'],
    ['\\delta', 'дельта', 'delta'],
    ['\\varepsilon', 'эпсилон', 'epsilon'],
    ['\\zeta', 'дзета', 'zeta'],
    ['\\eta', 'эта', 'eta', 'кпд'],
    ['\\theta', 'тета', 'theta'],
    ['\\iota', 'йота', 'iota'],
    ['\\kappa', 'каппа', 'kappa'],
    ['\\lambda', 'лямбда', 'lambda', 'длина волны'],
    ['\\mu', 'мю', 'mu', 'коэффициент трения'],
    ['\\nu', 'ню', 'nu', 'частота'],
    ['\\xi', 'кси', 'xi'],
    ['\\pi', 'пи', 'pi'],
    ['\\rho', 'ро', 'rho', 'плотность'],
    ['\\sigma', 'сигма', 'sigma'],
    ['\\tau', 'тау', 'tau'],
    ['\\upsilon', 'ипсилон', 'upsilon'],
    ['\\varphi', 'фи', 'phi', 'угол фаза'],
    ['\\chi', 'хи', 'chi'],
    ['\\psi', 'пси', 'psi'],
    ['\\omega', 'омега', 'omega', 'угловая скорость'],
    ['\\Gamma', 'Гамма', 'Gamma'],
    ['\\Delta', 'Дельта', 'Delta', 'изменение приращение'],
    ['\\Theta', 'Тета', 'Theta'],
    ['\\Lambda', 'Лямбда', 'Lambda'],
    ['\\Pi', 'Пи', 'Pi', 'произведение'],
    ['\\Sigma', 'Сигма', 'Sigma'],
    ['\\Phi', 'Фи', 'Phi', 'магнитный поток'],
    ['\\Psi', 'Пси', 'Psi'],
    ['\\Omega', 'Омега', 'Omega', 'ом'],
  ]),
  group('sets', 'Множества и логика', 'Sets & logic', [
    ['\\in', 'Принадлежит', 'Element of'],
    ['\\notin', 'Не принадлежит', 'Not element of'],
    ['\\subset', 'Подмножество', 'Subset'],
    ['\\subseteq', 'Подмножество или равно', 'Subset or equal'],
    ['\\cup', 'Объединение', 'Union'],
    ['\\cap', 'Пересечение', 'Intersection'],
    ['\\setminus', 'Разность множеств', 'Set difference'],
    ['\\varnothing', 'Пустое множество', 'Empty set'],
    ['\\mathbb{N}', 'Натуральные', 'Naturals'],
    ['\\mathbb{Z}', 'Целые', 'Integers'],
    ['\\mathbb{Q}', 'Рациональные', 'Rationals'],
    ['\\mathbb{R}', 'Действительные', 'Reals'],
    ['\\mathbb{C}', 'Комплексные', 'Complex'],
    ['\\left\\lbrace #0\\mid #?\\right\\rbrace', 'Множество с условием', 'Set-builder'],
    ['\\left[#0;#?\\right]', 'Отрезок [a; b]', 'Closed interval', 'промежуток'],
    ['\\left(#0;#?\\right)', 'Интервал (a; b)', 'Open interval', 'промежуток'],
    ['\\forall', 'Для любого', 'For all', 'квантор'],
    ['\\exists', 'Существует', 'Exists', 'квантор'],
    ['\\neg', 'Отрицание', 'Not', 'не'],
    ['\\land', 'И (конъюнкция)', 'And'],
    ['\\lor', 'Или (дизъюнкция)', 'Or'],
    ['\\oplus', 'Исключающее или', 'Xor'],
    ['\\Rightarrow', 'Следует', 'Implies', 'следовательно'],
    ['\\Leftrightarrow', 'Равносильно', 'If and only if', 'тогда и только тогда'],
  ]),
  group('arrows', 'Стрелки', 'Arrows', [
    ['\\to', 'Стремится', 'To', 'вправо'],
    ['\\leftarrow', 'Влево', 'Left arrow'],
    ['\\leftrightarrow', 'В обе стороны', 'Left-right arrow'],
    ['\\uparrow', 'Вверх (газ)', 'Up (gas)', 'газ'],
    ['\\downarrow', 'Вниз (осадок)', 'Down (precipitate)', 'осадок'],
    ['\\mapsto', 'Отображается в', 'Maps to'],
    ['\\rightleftharpoons', 'Обратимая реакция', 'Equilibrium', 'химия'],
    ['\\xrightarrow{#?}', 'Стрелка с условием', 'Arrow with label', 'химия катализатор t'],
    ['\\Leftarrow', 'Обратное следствие', 'Is implied by'],
  ]),
  group('calculus', 'Анализ', 'Calculus', [
    ['\\lim_{#?\\to #?}#0', 'Предел', 'Limit'],
    ['\\lim_{x\\to\\infty}#0', 'Предел при x → ∞', 'Limit to infinity'],
    ['\\sum_{#?}^{#?}#0', 'Сумма', 'Sum', 'сигма ряд'],
    ['\\prod_{#?}^{#?}#0', 'Произведение', 'Product'],
    ['\\int #0\\,d#?', 'Интеграл', 'Integral', 'неопределённый первообразная'],
    ['\\int_{#?}^{#?}#0\\,d#?', 'Определённый интеграл', 'Definite integral'],
    ['\\iint #0\\,d#?\\,d#?', 'Двойной интеграл', 'Double integral'],
    ['\\oint #0', 'Интеграл по контуру', 'Contour integral'],
    ['\\left.#0\\right|_{#?}^{#?}', 'Подстановка', 'Evaluated at', 'ньютон лейбниц'],
    ["#0'", 'Производная (штрих)', 'Derivative (prime)'],
    ["#0''", 'Вторая производная', 'Second derivative'],
    ['\\frac{d#0}{d#?}', 'Производная d/dx', 'Derivative d/dx'],
    ['\\frac{\\partial #0}{\\partial #?}', 'Частная производная', 'Partial derivative'],
    ['\\nabla', 'Набла', 'Nabla', 'градиент'],
    ['\\log_{#?}#0', 'Логарифм', 'Logarithm', 'log'],
    ['\\ln #0', 'Натуральный логарифм', 'Natural log'],
    ['\\lg #0', 'Десятичный логарифм', 'Common log'],
    ['e^{#0}', 'Экспонента', 'Exponential'],
    ['\\sin #0', 'Синус', 'Sine'],
    ['\\cos #0', 'Косинус', 'Cosine'],
    ['\\operatorname{tg}#0', 'Тангенс', 'Tangent (tg)', 'tan'],
    ['\\operatorname{ctg}#0', 'Котангенс', 'Cotangent (ctg)', 'cot'],
    ['\\arcsin #0', 'Арксинус', 'Arcsine'],
    ['\\arccos #0', 'Арккосинус', 'Arccosine'],
    ['\\operatorname{arctg}#0', 'Арктангенс', 'Arctangent', 'arctan'],
    ['\\max_{#?}#0', 'Максимум', 'Maximum'],
    ['\\min_{#?}#0', 'Минимум', 'Minimum'],
  ]),
  group('structures', 'Матрицы и системы', 'Matrices & systems', [
    ['\\begin{cases}#0\\\\#?\\end{cases}', 'Система (2 уравнения)', 'System of 2', 'система'],
    ['\\begin{cases}#0\\\\#?\\\\#?\\end{cases}', 'Система (3 уравнения)', 'System of 3', 'система'],
    [
      '\\left[\\begin{aligned}&#0\\\\&#?\\end{aligned}\\right.',
      'Совокупность',
      'Disjunction of equations',
      'или',
    ],
    ['#0=\\begin{cases}#? & #?\\\\#? & #?\\end{cases}', 'Кусочная функция', 'Piecewise function'],
    ['\\begin{pmatrix}#0&#?\\\\#?&#?\\end{pmatrix}', 'Матрица 2×2', 'Matrix 2×2'],
    ['\\begin{pmatrix}#0&#?&#?\\\\#?&#?&#?\\\\#?&#?&#?\\end{pmatrix}', 'Матрица 3×3', 'Matrix 3×3'],
    ['\\begin{vmatrix}#0&#?\\\\#?&#?\\end{vmatrix}', 'Определитель 2×2', 'Determinant 2×2'],
    [
      '\\begin{vmatrix}#0&#?&#?\\\\#?&#?&#?\\\\#?&#?&#?\\end{vmatrix}',
      'Определитель 3×3',
      'Determinant 3×3',
    ],
    ['\\begin{pmatrix}#0\\\\#?\\end{pmatrix}', 'Столбец', 'Column vector', 'координаты'],
    ['\\binom{#0}{#?}', 'Биномиальный коэффициент', 'Binomial'],
    ['C_{#?}^{#0}', 'Сочетания', 'Combinations', 'комбинаторика'],
    ['A_{#?}^{#0}', 'Размещения', 'Arrangements', 'комбинаторика'],
    ['P_{#0}', 'Перестановки', 'Permutations', 'комбинаторика'],
  ]),
  group('geometry', 'Геометрия', 'Geometry', [
    ['\\angle #0', 'Угол', 'Angle'],
    ['\\triangle #0', 'Треугольник', 'Triangle'],
    ['\\overline{#0}', 'Отрезок', 'Segment'],
    ['\\overrightarrow{#0}', 'Вектор AB', 'Vector AB'],
    ['\\parallel', 'Параллельно', 'Parallel'],
    ['\\perp', 'Перпендикулярно', 'Perpendicular'],
    ['\\sim', 'Подобен', 'Similar'],
    ['\\cong', 'Равен', 'Congruent'],
    ['\\overset{\\frown}{#0}', 'Дуга', 'Arc'],
    ['#0^{\\circ}', 'Градусы', 'Degrees'],
    ["#0'", 'Минуты', 'Minutes'],
    ['\\odot', 'Окружность', 'Circle'],
    ['\\square', 'Квадрат', 'Square'],
  ]),
  group('chemistry', 'Химия', 'Chemistry', [
    ['\\mathrm{#0}', 'Формула вещества', 'Substance', 'прямой шрифт'],
    ['\\mathrm{#0}_{#?}', 'Индекс атомов', 'Atom count'],
    ['\\mathrm{#0}^{#?+}', 'Катион', 'Cation', 'ион заряд'],
    ['\\mathrm{#0}^{#?-}', 'Анион', 'Anion', 'ион заряд'],
    ['\\overset{#?}{\\mathrm{#0}}', 'Степень окисления', 'Oxidation state'],
    ['\\rightarrow', 'Реакция', 'Reaction arrow'],
    ['\\rightleftharpoons', 'Обратимая реакция', 'Equilibrium'],
    ['\\xrightarrow{#?}', 'Условие реакции', 'Reaction condition', 'катализатор нагревание t'],
    ['\\uparrow', 'Газ', 'Gas'],
    ['\\downarrow', 'Осадок', 'Precipitate'],
    ['\\Delta H', 'Тепловой эффект', 'Enthalpy'],
  ]),
];

// ─── Library: ready formulas ─────────────────────────────────────────────────

export const LIBRARY_GROUPS: readonly FormulaGroup[] = [
  group('algebra', 'Алгебра', 'Algebra', [
    ['ax^2+bx+c=0', 'Квадратное уравнение', 'Quadratic equation'],
    ['D=b^2-4ac', 'Дискриминант', 'Discriminant'],
    ['x_{1,2}=\\frac{-b\\pm\\sqrt{D}}{2a}', 'Корни квадратного уравнения', 'Quadratic formula'],
    ['x_1+x_2=-\\frac{b}{a},\\quad x_1x_2=\\frac{c}{a}', 'Теорема Виета', "Vieta's formulas"],
    ['(a+b)^2=a^2+2ab+b^2', 'Квадрат суммы', 'Square of a sum'],
    ['(a-b)^2=a^2-2ab+b^2', 'Квадрат разности', 'Square of a difference'],
    ['a^2-b^2=(a-b)(a+b)', 'Разность квадратов', 'Difference of squares'],
    ['(a+b)^3=a^3+3a^2b+3ab^2+b^3', 'Куб суммы', 'Cube of a sum'],
    ['a^3+b^3=(a+b)(a^2-ab+b^2)', 'Сумма кубов', 'Sum of cubes'],
    ['a^3-b^3=(a-b)(a^2+ab+b^2)', 'Разность кубов', 'Difference of cubes'],
    ['a^m\\cdot a^n=a^{m+n}', 'Умножение степеней', 'Product of powers'],
    ['\\left(a^m\\right)^n=a^{mn}', 'Степень степени', 'Power of a power'],
    ['a^{-n}=\\frac{1}{a^n}', 'Отрицательная степень', 'Negative exponent'],
    ['a^{\\frac{m}{n}}=\\sqrt[n]{a^m}', 'Дробная степень', 'Rational exponent'],
    ['\\log_a b=c\\Leftrightarrow a^c=b', 'Определение логарифма', 'Logarithm definition'],
    ['\\log_a(xy)=\\log_a x+\\log_a y', 'Логарифм произведения', 'Log of a product'],
    ['\\log_a b=\\frac{\\log_c b}{\\log_c a}', 'Переход к новому основанию', 'Change of base'],
    ['a_n=a_1+(n-1)d', 'n-й член арифм. прогрессии', 'Arithmetic sequence term'],
    ['S_n=\\frac{(a_1+a_n)n}{2}', 'Сумма арифм. прогрессии', 'Arithmetic series sum'],
    ['b_n=b_1q^{n-1}', 'n-й член геом. прогрессии', 'Geometric sequence term'],
    ['S_n=\\frac{b_1\\left(q^n-1\\right)}{q-1}', 'Сумма геом. прогрессии', 'Geometric series sum'],
    ['S=\\frac{b_1}{1-q},\\ |q|<1', 'Бесконечно убывающая прогрессия', 'Infinite geometric series'],
    [
      '\\left|a\\right|=\\begin{cases}a, & a\\geq 0\\\\-a, & a<0\\end{cases}',
      'Модуль числа',
      'Absolute value',
    ],
    ['C_n^k=\\frac{n!}{k!\\,(n-k)!}', 'Число сочетаний', 'Combinations'],
    ['P(A)=\\frac{m}{n}', 'Классическая вероятность', 'Classical probability'],
  ]),
  group('trigonometry', 'Тригонометрия', 'Trigonometry', [
    ['\\sin^2\\alpha+\\cos^2\\alpha=1', 'Основное тождество', 'Pythagorean identity'],
    ['\\operatorname{tg}\\alpha=\\frac{\\sin\\alpha}{\\cos\\alpha}', 'Тангенс', 'Tangent'],
    [
      '1+\\operatorname{tg}^2\\alpha=\\frac{1}{\\cos^2\\alpha}',
      'Тангенс и косинус',
      'Tangent–secant identity',
    ],
    ['\\sin 2\\alpha=2\\sin\\alpha\\cos\\alpha', 'Синус двойного угла', 'Double angle (sin)'],
    ['\\cos 2\\alpha=\\cos^2\\alpha-\\sin^2\\alpha', 'Косинус двойного угла', 'Double angle (cos)'],
    [
      '\\sin(\\alpha\\pm\\beta)=\\sin\\alpha\\cos\\beta\\pm\\cos\\alpha\\sin\\beta',
      'Синус суммы',
      'Sine of a sum',
    ],
    [
      '\\cos(\\alpha\\pm\\beta)=\\cos\\alpha\\cos\\beta\\mp\\sin\\alpha\\sin\\beta',
      'Косинус суммы',
      'Cosine of a sum',
    ],
    [
      '\\sin x=a\\Rightarrow x=(-1)^n\\arcsin a+\\pi n,\\ n\\in\\mathbb{Z}',
      'Уравнение sin x = a',
      'Solving sin x = a',
    ],
    [
      '\\cos x=a\\Rightarrow x=\\pm\\arccos a+2\\pi n,\\ n\\in\\mathbb{Z}',
      'Уравнение cos x = a',
      'Solving cos x = a',
    ],
    [
      '\\operatorname{tg}x=a\\Rightarrow x=\\operatorname{arctg}a+\\pi n,\\ n\\in\\mathbb{Z}',
      'Уравнение tg x = a',
      'Solving tan x = a',
    ],
  ]),
  group('geometry', 'Геометрия', 'Geometry', [
    ['c^2=a^2+b^2', 'Теорема Пифагора', 'Pythagorean theorem'],
    ['a^2=b^2+c^2-2bc\\cos\\alpha', 'Теорема косинусов', 'Law of cosines'],
    [
      '\\frac{a}{\\sin\\alpha}=\\frac{b}{\\sin\\beta}=\\frac{c}{\\sin\\gamma}=2R',
      'Теорема синусов',
      'Law of sines',
    ],
    ['S=\\frac{1}{2}ah', 'Площадь треугольника', 'Triangle area'],
    ['S=\\frac{1}{2}ab\\sin\\gamma', 'Площадь через синус', 'Area via sine'],
    ['S=\\sqrt{p(p-a)(p-b)(p-c)}', 'Формула Герона', "Heron's formula"],
    ['S=\\pi r^2', 'Площадь круга', 'Circle area'],
    ['C=2\\pi r', 'Длина окружности', 'Circumference'],
    ['S=\\frac{a+b}{2}\\cdot h', 'Площадь трапеции', 'Trapezoid area'],
    ['V=\\frac{1}{3}S h', 'Объём пирамиды/конуса', 'Pyramid/cone volume'],
    ['V=\\frac{4}{3}\\pi R^3', 'Объём шара', 'Sphere volume'],
    ['S=4\\pi R^2', 'Площадь сферы', 'Sphere area'],
    ['\\left|\\vec{a}\\right|=\\sqrt{x^2+y^2}', 'Длина вектора', 'Vector length'],
    [
      '\\vec{a}\\cdot\\vec{b}=\\left|\\vec{a}\\right|\\left|\\vec{b}\\right|\\cos\\varphi',
      'Скалярное произведение',
      'Dot product',
    ],
    ['(x-a)^2+(y-b)^2=R^2', 'Уравнение окружности', 'Circle equation'],
  ]),
  group('calculus', 'Начала анализа', 'Calculus', [
    ["(x^n)'=nx^{n-1}", 'Производная степени', 'Power rule'],
    ["(uv)'=u'v+uv'", 'Производная произведения', 'Product rule'],
    ["\\left(\\frac{u}{v}\\right)'=\\frac{u'v-uv'}{v^2}", 'Производная частного', 'Quotient rule'],
    ["(f(g(x)))'=f'(g(x))\\cdot g'(x)", 'Производная сложной функции', 'Chain rule'],
    ["(\\sin x)'=\\cos x", 'Производная синуса', 'Derivative of sin'],
    ["(e^x)'=e^x", 'Производная экспоненты', 'Derivative of exp'],
    ["(\\ln x)'=\\frac{1}{x}", 'Производная логарифма', 'Derivative of ln'],
    ["y=f(x_0)+f'(x_0)(x-x_0)", 'Уравнение касательной', 'Tangent line'],
    ['\\int x^n\\,dx=\\frac{x^{n+1}}{n+1}+C', 'Интеграл степени', 'Power integral'],
    ['\\int_a^b f(x)\\,dx=F(b)-F(a)', 'Формула Ньютона–Лейбница', 'Fundamental theorem'],
    [
      '\\lim_{x\\to 0}\\frac{\\sin x}{x}=1',
      'Первый замечательный предел',
      'First remarkable limit',
    ],
    [
      '\\lim_{n\\to\\infty}\\left(1+\\frac{1}{n}\\right)^n=e',
      'Второй замечательный предел',
      'Second remarkable limit',
    ],
  ]),
  group('physics', 'Физика', 'Physics', [
    ['v=\\frac{s}{t}', 'Скорость', 'Speed'],
    ['s=v_0t+\\frac{at^2}{2}', 'Путь при равноускоренном', 'Uniform acceleration'],
    ['v=v_0+at', 'Скорость при равноускоренном', 'Velocity under acceleration'],
    ['\\vec{F}=m\\vec{a}', 'Второй закон Ньютона', "Newton's second law"],
    ['F=G\\frac{m_1m_2}{r^2}', 'Закон всемирного тяготения', 'Law of gravitation'],
    ['F_{\\text{тр}}=\\mu N', 'Сила трения', 'Friction force'],
    ['F=-kx', 'Закон Гука', "Hooke's law"],
    ['p=mv', 'Импульс', 'Momentum'],
    ['E_k=\\frac{mv^2}{2}', 'Кинетическая энергия', 'Kinetic energy'],
    ['E_p=mgh', 'Потенциальная энергия', 'Potential energy'],
    ['A=Fs\\cos\\alpha', 'Работа', 'Work'],
    ['N=\\frac{A}{t}', 'Мощность', 'Power'],
    ['p=\\rho gh', 'Гидростатическое давление', 'Hydrostatic pressure'],
    ['F_A=\\rho gV', 'Сила Архимеда', 'Buoyant force'],
    ['pV=\\frac{m}{M}RT', 'Уравнение Менделеева–Клапейрона', 'Ideal gas law'],
    ['Q=cm\\Delta t', 'Количество теплоты', 'Heat'],
    ['\\eta=\\frac{A_{\\text{п}}}{A_{\\text{з}}}\\cdot 100\\%', 'КПД', 'Efficiency'],
    ['I=\\frac{U}{R}', 'Закон Ома', "Ohm's law"],
    ['I=\\frac{\\varepsilon}{R+r}', 'Закон Ома для полной цепи', "Ohm's law (full circuit)"],
    ['F=k\\frac{|q_1||q_2|}{r^2}', 'Закон Кулона', "Coulomb's law"],
    ['P=UI', 'Мощность тока', 'Electric power'],
    ['Q=I^2Rt', 'Закон Джоуля–Ленца', 'Joule heating'],
    ['T=2\\pi\\sqrt{\\frac{l}{g}}', 'Период маятника', 'Pendulum period'],
    ['\\lambda=\\frac{v}{\\nu}', 'Длина волны', 'Wavelength'],
    ['E=h\\nu', 'Энергия фотона', 'Photon energy'],
    ['E=mc^2', 'Энергия покоя', 'Mass–energy equivalence'],
  ]),
  group('chemistry', 'Химия', 'Chemistry', [
    ['n=\\frac{m}{M}', 'Количество вещества', 'Amount of substance'],
    ['n=\\frac{V}{V_m}', 'Через молярный объём', 'Via molar volume'],
    [
      '\\omega=\\frac{m_{\\text{в-ва}}}{m_{\\text{р-ра}}}\\cdot 100\\%',
      'Массовая доля',
      'Mass fraction',
    ],
    ['c=\\frac{n}{V}', 'Молярная концентрация', 'Molar concentration'],
    ['\\mathrm{2H_2+O_2\\rightarrow 2H_2O}', 'Горение водорода', 'Hydrogen combustion'],
    ['\\mathrm{CH_4+2O_2\\rightarrow CO_2+2H_2O}', 'Горение метана', 'Methane combustion'],
    [
      '\\mathrm{CaCO_3\\xrightarrow{t^{\\circ}}CaO+CO_2\\uparrow}',
      'Разложение карбоната',
      'Carbonate decomposition',
    ],
    [
      '\\mathrm{Ag^++Cl^-\\rightarrow AgCl\\downarrow}',
      'Ионное уравнение (осадок)',
      'Precipitation',
    ],
    ['\\mathrm{N_2+3H_2\\rightleftharpoons 2NH_3}', 'Синтез аммиака', 'Ammonia synthesis'],
    ['\\mathrm{pH}=-\\lg\\left[\\mathrm{H^+}\\right]', 'Водородный показатель', 'pH'],
  ]),
];

/** Case-insensitive search across labels (both languages), keywords and LaTeX; no duplicates. */
export function searchFormulas(groups: readonly FormulaGroup[], query: string): FormulaEntry[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return [];
  const byLatex = new Map<string, FormulaEntry>();
  for (const entry of groups.flatMap((formulaGroup) => formulaGroup.items)) {
    if (byLatex.has(entry.latex)) continue;
    const haystack =
      `${entry.label.ru} ${entry.label.en} ${entry.keywords ?? ''} ${entry.latex}`.toLowerCase();
    if (haystack.includes(needle)) byLatex.set(entry.latex, entry);
  }
  return [...byLatex.values()];
}
