export type SessionEvent = 'unauthorized' | 'passwordChangeRequired';
type Listener = (event: SessionEvent) => void;

const listeners = new Set<Listener>();

/** The API layer reports session problems here; the auth provider reacts (no import cycle). */
export function onSessionEvent(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function emitSessionEvent(event: SessionEvent) {
  listeners.forEach((l) => l(event));
}
