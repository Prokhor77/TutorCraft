import { create } from 'zustand';

type SaveState = {
  /** Epoch ms of the last successful server write in this tab (mutation or autosave); null until one happens. */
  lastSavedAt: number | null;
  markSaved: (at?: number) => void;
};

/** Feeds the header «Сохранено N мин назад» indicator (Stitch) from real saves only. */
export const useSaveStore = create<SaveState>((set) => ({
  lastSavedAt: null,
  markSaved: (at = Date.now()) => set({ lastSavedAt: at }),
}));
