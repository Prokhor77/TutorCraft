/** Структура юридического документа: нумерованные разделы из простых блоков, которые рендерит `LegalDocument`. */
export type LegalBlock =
  | { type: 'text'; value: string }
  | { type: 'list'; items: string[] }
  | { type: 'table'; columns: string[]; rows: string[][] }
  /** Реквизиты правообладателя из `organization.ts`. */
  | { type: 'contacts' };

export type LegalSection = { id: string; title: string; blocks: LegalBlock[] };

export type LegalDocumentContent = {
  title: string;
  /** Короткий подзаголовок под H1 (для кого документ). */
  lead: string;
  sections: LegalSection[];
};
