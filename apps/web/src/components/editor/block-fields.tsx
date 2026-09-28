'use client';
import { Minus, Plus, Sigma, Upload } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef } from 'react';
import { EmbedFrame } from '@/components/blockdoc/embed-frame';
import { MathView } from '@/components/blockdoc/math-view';
import { BlockView } from '@/components/blockdoc/block-renderer';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import { CALLOUT_TONES, type Block, type BlockOf, type RichText } from '@/lib/api/schemas/blockdoc';
import { cn } from '@/lib/utils/cn';
import { useFormulaEditorStore } from '@/stores/formula-editor-store';
import { RichTextEditable, type RichTextEditableHandle } from './rich-text-editable';

export type BlockFieldProps<T extends Block['type']> = {
  block: BlockOf<T>;
  onChange: (next: Block) => void;
  autoFocus?: boolean;
  onEnter?: () => void;
  onBackspaceEmpty?: () => void;
  onSlash?: () => void;
  editableRef?: React.Ref<RichTextEditableHandle>;
  invalid?: boolean;
};

const headingClass = {
  1: 'text-3xl font-semibold',
  2: 'text-2xl font-semibold',
  3: 'text-xl font-semibold',
} as const;

function isEmpty(text: RichText): boolean {
  return text.every((span) => span.text.trim() === '');
}

function textKeyHandler(
  text: RichText,
  props: Pick<BlockFieldProps<'paragraph'>, 'onEnter' | 'onBackspaceEmpty' | 'onSlash'>,
) {
  return (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && props.onEnter) {
      event.preventDefault();
      props.onEnter();
    } else if (event.key === 'Backspace' && isEmpty(text) && props.onBackspaceEmpty) {
      event.preventDefault();
      props.onBackspaceEmpty();
    } else if (event.key === '/' && isEmpty(text) && props.onSlash) {
      event.preventDefault();
      props.onSlash();
    }
  };
}

export function TextBlockField(
  props: BlockFieldProps<'paragraph' | 'heading' | 'quote' | 'callout'>,
) {
  const t = useTranslations('editor');
  const { block, onChange } = props;
  const className =
    block.type === 'heading'
      ? headingClass[block.level]
      : block.type === 'quote'
        ? 'border-l-4 border-border pl-4 italic text-text-muted'
        : block.type === 'callout'
          ? 'flex-1'
          : 'leading-relaxed';
  const editable = (
    <RichTextEditable
      ref={props.editableRef}
      value={block.text}
      onChange={(text) => onChange({ ...block, text })}
      ariaLabel={t(`blockTypes.${block.type}`)}
      placeholder={
        block.type === 'paragraph' ? t('placeholders.paragraph') : t(`placeholders.${block.type}`)
      }
      className={className}
      autoFocus={props.autoFocus}
      onKeyDown={textKeyHandler(block.text, props)}
    />
  );
  if (block.type !== 'callout') return editable;
  return (
    <div className="flex flex-col gap-2 rounded bg-surface-muted p-3 sm:flex-row sm:items-start">
      <NativeSelect
        aria-label={t('calloutTone')}
        value={block.tone}
        onChange={(event) =>
          onChange({ ...block, tone: event.target.value as (typeof CALLOUT_TONES)[number] })
        }
        className="h-8 w-auto text-xs"
      >
        {CALLOUT_TONES.map((tone) => (
          <option key={tone} value={tone}>
            {t(`tones.${tone}`)}
          </option>
        ))}
      </NativeSelect>
      {editable}
    </div>
  );
}

export function ListBlockField({
  block,
  onChange,
  autoFocus,
  onBackspaceEmpty,
}: BlockFieldProps<'list'>) {
  const t = useTranslations('editor');
  const Tag = block.ordered ? 'ol' : 'ul';
  const setItem = (index: number, text: RichText) =>
    onChange({ ...block, items: block.items.map((item, i) => (i === index ? text : item)) });
  return (
    <Tag className={cn('flex flex-col gap-1 pl-6', block.ordered ? 'list-decimal' : 'list-disc')}>
      {block.items.map((item, index) => (
        <li key={index}>
          <RichTextEditable
            value={item}
            ariaLabel={t('listItem', { index: index + 1 })}
            placeholder={t('placeholders.list')}
            autoFocus={autoFocus && index === block.items.length - 1}
            onChange={(text) => setItem(index, text)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                onChange({
                  ...block,
                  items: [...block.items.slice(0, index + 1), [], ...block.items.slice(index + 1)],
                });
              } else if (event.key === 'Backspace' && isEmpty(item)) {
                event.preventDefault();
                if (block.items.length === 1) onBackspaceEmpty?.();
                else onChange({ ...block, items: block.items.filter((_, i) => i !== index) });
              }
            }}
          />
        </li>
      ))}
    </Tag>
  );
}

export function CodeBlockField({ block, onChange, autoFocus }: BlockFieldProps<'code'>) {
  const t = useTranslations('editor');
  return (
    <div className="flex flex-col gap-2 rounded bg-surface-muted p-3">
      <Input
        aria-label={t('codeLanguage')}
        value={block.language}
        onChange={(event) => onChange({ ...block, language: event.target.value })}
        className="h-8 w-40 text-xs"
      />
      <Textarea
        aria-label={t('blockTypes.code')}
        value={block.code}
        autoFocus={autoFocus}
        spellCheck={false}
        onChange={(event) => onChange({ ...block, code: event.target.value })}
        className="min-h-28 font-mono text-sm"
        onKeyDown={(event) => {
          if (event.key !== 'Tab') return;
          event.preventDefault();
          const target = event.currentTarget;
          const { selectionStart, selectionEnd, value } = target;
          onChange({
            ...block,
            code: `${value.slice(0, selectionStart)}  ${value.slice(selectionEnd)}`,
          });
        }}
      />
    </div>
  );
}

export function MathBlockField({ block, onChange, autoFocus }: BlockFieldProps<'math'>) {
  const t = useTranslations('editor');
  const tMath = useTranslations('math');
  const openFormulaEditor = useFormulaEditorStore((state) => state.open);
  const openEditor = useCallback(
    () =>
      openFormulaEditor({
        initialLatex: block.latex,
        onSubmit: (latex) => onChange({ ...block, latex }),
      }),
    [openFormulaEditor, block, onChange],
  );
  const autoOpened = useRef(false);
  useEffect(() => {
    if (!autoFocus || block.latex || autoOpened.current) return;
    autoOpened.current = true;
    openEditor();
  }, [autoFocus, block.latex, openEditor]);

  return (
    <div className="flex flex-col gap-2 rounded bg-surface-muted p-3">
      <button
        type="button"
        onClick={openEditor}
        aria-label={tMath('openEditor')}
        className="flex min-h-16 items-center justify-center rounded bg-surface p-2 transition-colors duration-fast hover:bg-primary-soft/40 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
      >
        {block.latex ? (
          <MathView latex={block.latex} className="pointer-events-none" />
        ) : (
          <span className="flex items-center gap-2 text-sm text-text-muted">
            <Sigma className="size-4" aria-hidden /> {tMath('openEditor')}
          </span>
        )}
      </button>
      <details className="text-xs text-text-muted">
        <summary className="cursor-pointer select-none">{t('latex')}</summary>
        <Textarea
          aria-label={t('latex')}
          placeholder="E = mc^2"
          value={block.latex}
          spellCheck={false}
          onChange={(event) => onChange({ ...block, latex: event.target.value })}
          className="mt-2 min-h-20 font-mono text-sm"
        />
      </details>
    </div>
  );
}

export function TableBlockField({ block, onChange }: BlockFieldProps<'table'>) {
  const t = useTranslations('editor');
  const columns = block.rows[0]?.length ?? 0;
  const setCell = (rowIndex: number, cellIndex: number, text: RichText) =>
    onChange({
      ...block,
      rows: block.rows.map((row, r) =>
        r === rowIndex ? row.map((cell, c) => (c === cellIndex ? text : cell)) : row,
      ),
    });
  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <tbody>
            {block.rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <td
                    key={cellIndex}
                    className="min-w-24 border border-border px-2 py-1.5 align-top"
                  >
                    <RichTextEditable
                      value={cell}
                      ariaLabel={t('tableCell', { row: rowIndex + 1, column: cellIndex + 1 })}
                      onChange={(text) => setCell(rowIndex, cellIndex, text)}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap gap-1">
        <Button
          size="sm"
          variant="ghost"
          onClick={() =>
            onChange({ ...block, rows: [...block.rows, Array.from({ length: columns }, () => [])] })
          }
        >
          <Plus aria-hidden /> {t('addRow')}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          onClick={() => onChange({ ...block, rows: block.rows.map((row) => [...row, []]) })}
        >
          <Plus aria-hidden /> {t('addColumn')}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          disabled={block.rows.length <= 1}
          onClick={() => onChange({ ...block, rows: block.rows.slice(0, -1) })}
        >
          <Minus aria-hidden /> {t('removeRow')}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          disabled={columns <= 1}
          onClick={() => onChange({ ...block, rows: block.rows.map((row) => row.slice(0, -1)) })}
        >
          <Minus aria-hidden /> {t('removeColumn')}
        </Button>
      </div>
    </div>
  );
}

export function ImageBlockField({ block, onChange, invalid }: BlockFieldProps<'image'>) {
  const t = useTranslations('editor');
  const altId = `alt-${block.id}`;
  return (
    <div className="flex flex-col gap-2">
      <BlockView block={block} />
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label htmlFor={altId} className="text-xs font-medium">
            {t('altText')} <span className="text-danger">*</span>
          </label>
          <Input
            id={altId}
            value={block.alt}
            required
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? `${altId}-error` : undefined}
            placeholder={t('altPlaceholder')}
            onChange={(event) => onChange({ ...block, alt: event.target.value })}
          />
          {invalid ? (
            <p id={`${altId}-error`} role="alert" className="text-xs text-danger">
              {t('issues.image_alt_required')}
            </p>
          ) : null}
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={`caption-${block.id}`} className="text-xs font-medium">
            {t('caption')}
          </label>
          <Input
            id={`caption-${block.id}`}
            value={block.caption ?? ''}
            onChange={(event) => onChange({ ...block, caption: event.target.value || undefined })}
          />
        </div>
      </div>
    </div>
  );
}

export function FileBlockField({ block }: BlockFieldProps<'file'>) {
  return <BlockView block={block} />;
}

export function VideoBlockField({
  block,
  onChange,
  invalid,
  onUploadVideo,
}: BlockFieldProps<'video'> & { onUploadVideo: () => void }) {
  const t = useTranslations('editor');
  if (block.fileId) return <BlockView block={block} />;
  return (
    <div
      className={cn(
        'flex flex-col gap-2 rounded border border-dashed border-border p-3',
        invalid && 'border-danger',
      )}
    >
      <Input
        aria-label={t('videoUrl')}
        placeholder="https://www.youtube.com/embed/…"
        value={block.embedUrl ?? ''}
        onChange={(event) => onChange({ ...block, embedUrl: event.target.value || undefined })}
      />
      <Button size="sm" variant="secondary" onClick={onUploadVideo} className="self-start">
        <Upload aria-hidden /> {t('uploadVideo')}
      </Button>
      {block.embedUrl ? <EmbedFrame url={block.embedUrl} title={t('blockTypes.video')} /> : null}
    </div>
  );
}

export function EmbedBlockField({ block, onChange, invalid, autoFocus }: BlockFieldProps<'embed'>) {
  const t = useTranslations('editor');
  return (
    <div className="flex flex-col gap-2">
      <Input
        aria-label={t('embedUrl')}
        placeholder="https://"
        value={block.url}
        autoFocus={autoFocus}
        aria-invalid={invalid || undefined}
        onChange={(event) => onChange({ ...block, url: event.target.value })}
      />
      <p className="text-xs text-text-muted">{t('embedHint')}</p>
      {block.url ? <EmbedFrame url={block.url} title={t('blockTypes.embed')} /> : null}
    </div>
  );
}
