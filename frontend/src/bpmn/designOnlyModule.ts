import type PopupMenu from 'diagram-js/lib/features/popup-menu/PopupMenu'
import type { PopupMenuEntries } from 'diagram-js/lib/features/popup-menu/PopupMenuProvider'
import type Palette from 'diagram-js/lib/features/palette/Palette'
import type { PaletteEntries } from 'diagram-js/lib/features/palette/PaletteProvider'
import type ContextPad from 'diagram-js/lib/features/context-pad/ContextPad'
import type { ContextPadEntries } from 'diagram-js/lib/features/context-pad/ContextPadProvider'

const ALLOWED_REPLACEMENTS = new Set([
  'replace-with-none-start', 'replace-with-none-start-event',
  'replace-with-none-intermediate-throw', 'replace-with-none-intermediate-throwing',
  'replace-with-none-end', 'replace-with-none-end-event',
  'replace-with-task', 'replace-with-user-task', 'replace-with-service-task', 'replace-with-send-task',
  'replace-with-receive-task', 'replace-with-manual-task', 'replace-with-rule-task',
  'replace-with-collapsed-subprocess', 'replace-with-expanded-subprocess', 'replace-with-subprocess',
  'replace-with-event-subprocess', 'replace-with-exclusive-gateway', 'replace-with-parallel-gateway',
  'replace-with-inclusive-gateway', 'replace-with-event-based-gateway',
  'replace-with-sequence-flow', 'replace-with-default-flow',
])
const HIDDEN_PALETTE_ENTRIES = new Set([
  'create.data-object', 'create.data-store', 'create.group', 'create.participant-expanded',
])
const HIDDEN_CONTEXT_ENTRIES = new Set([
  'append.message-intermediate-event', 'append.timer-intermediate-event',
  'append.condition-intermediate-event', 'append.signal-intermediate-event', 'append.compensation-activity',
])

/** Do not offer choices the draft validator would immediately reject. */
class DesignOnlyMenuProvider {
  static $inject = ['popupMenu', 'palette', 'contextPad']

  constructor(popupMenu: PopupMenu, palette: Palette, contextPad: ContextPad) {
    popupMenu.registerProvider('bpmn-replace', 500, this)
    palette.registerProvider(500, this)
    contextPad.registerProvider(500, this)
  }

  getPopupMenuEntries(): (entries: PopupMenuEntries) => PopupMenuEntries {
    return (entries) => Object.fromEntries(Object.entries(entries).filter(([id]) => ALLOWED_REPLACEMENTS.has(id)))
  }

  // Current diagram-js invokes this map-updater API; hide loop/multi-instance controls.
  getPopupMenuHeaderEntries(): () => Record<string, never> {
    return () => ({})
  }

  getPaletteEntries(): (entries: PaletteEntries) => PaletteEntries {
    return (entries) => Object.fromEntries(Object.entries(entries).filter(([id]) => !HIDDEN_PALETTE_ENTRIES.has(id)))
  }

  getContextPadEntries(): (entries: ContextPadEntries) => ContextPadEntries {
    return (entries) => Object.fromEntries(Object.entries(entries).filter(([id]) => !HIDDEN_CONTEXT_ENTRIES.has(id)))
  }
}

export const designOnlyModule = {
  __init__: ['servicehubDesignOnlyMenu'],
  servicehubDesignOnlyMenu: ['type', DesignOnlyMenuProvider],
}
