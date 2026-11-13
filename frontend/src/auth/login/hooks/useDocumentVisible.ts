import { useSyncExternalStore } from 'react';

function subscribe(onChange: () => void): () => void {
  document.addEventListener('visibilitychange', onChange);
  return () => document.removeEventListener('visibilitychange', onChange);
}

function isVisible(): boolean {
  return typeof document === 'undefined' || document.visibilityState !== 'hidden';
}

/** Whether the tab is visible; decorative loops pause while it is hidden (AMB-2, spec §6.2). */
export function useDocumentVisible(): boolean {
  return useSyncExternalStore(subscribe, isVisible, () => true);
}
