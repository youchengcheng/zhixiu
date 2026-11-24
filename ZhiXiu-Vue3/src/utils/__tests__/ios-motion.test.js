import test from 'node:test'
import assert from 'node:assert/strict'
import { installPressFeedback, iosReveal, springFrames, springProgress } from '../ios-motion.js'

let reduce = false
globalThis.window = { innerHeight: 800, matchMedia: () => ({ matches: reduce }) }
globalThis.document = { hidden: false }
globalThis.getComputedStyle = () => ({ transform: 'matrix(0.965, 0, 0, 0.965, 0, 0)' })

function target(disabled = false) {
  const animations = []
  return {
    isConnected: true, animations,
    closest() { return this },
    matches: () => disabled,
    contains: () => false,
    getAnimations: () => animations.filter(animation => !animation.cancelled),
    getBoundingClientRect: () => ({ top: 20, bottom: 100 }),
    animate(frames, options) {
      const animation = { frames, options, cancelled: false, cancel() { this.cancelled = true; this.oncancel?.() } }
      animations.push(animation)
      return animation
    }
  }
}

function events() {
  const handlers = new Map()
  return { handlers, addEventListener: (name, handler) => handlers.set(name, handler), removeEventListener: name => handlers.delete(name) }
}

test('spring settles exactly at the target with a bounded rebound', () => {
  const samples = Array.from({ length: 101 }, (_, i) => springProgress(i / 100))
  assert.equal(samples[0], 0)
  assert.equal(samples.at(-1), 1)
  assert(Math.min(...samples) >= 0)
  assert(Math.max(...samples) > 1 && Math.max(...samples) < 1.1)
  const frames = springFrames([12, 44], [200, 88], ([x, y]) => ({ x, y }))
  assert.deepEqual(frames[0], { x: 12, y: 44, offset: 0 })
  assert.deepEqual(frames.at(-1), { x: 200, y: 88, offset: 1 })
})

test('press release, pointer cancellation and unmount never leave a control compressed', () => {
  const root = { ...events(), defaultView: events() }
  const button = target()
  const cleanup = installPressFeedback(root)
  root.handlers.get('pointerdown')({ target: button, button: 0, pointerId: 1 })
  const held = button.animations[0]
  held.onfinish()
  root.handlers.get('pointerup')({ pointerId: 2 })
  assert.equal(held.cancelled, false, 'a second pointer cannot release the active press')
  root.handlers.get('pointercancel')({ pointerId: 1 })
  assert.equal(held.cancelled, true)
  assert.equal(button.animations[1].frames.at(-1).transform, 'scale(1)')
  root.handlers.get('pointerdown')({ target: button, button: 0, pointerId: 3 })
  button.animations.at(-1).onfinish()
  cleanup()
  assert(button.animations.every(animation => animation.cancelled))
  assert.equal(root.handlers.size, 0)
  assert.equal(root.defaultView.handlers.size, 0)
})

test('disabled controls and reduced-motion preferences bypass press animations', () => {
  const root = events()
  const disabled = target(true)
  const button = target()
  const cleanup = installPressFeedback(root)
  root.handlers.get('pointerdown')({ target: disabled, button: 0 })
  root.handlers.get('pointerdown')({ target: button, button: 2 })
  reduce = true
  root.handlers.get('pointerdown')({ target: button, button: 0 })
  reduce = false
  assert.equal(disabled.animations.length, 0)
  assert.equal(button.animations.length, 0)
  cleanup()
})

test('asynchronous list insertion reveals new content once and disconnects on unmount', () => {
  const frames = new Map()
  let nextFrame = 0
  let observer
  globalThis.requestAnimationFrame = callback => { frames.set(++nextFrame, callback); return nextFrame }
  globalThis.cancelAnimationFrame = id => frames.delete(id)
  globalThis.MutationObserver = class {
    constructor(callback) { this.callback = callback; observer = this }
    observe() {}
    disconnect() { this.disconnected = true }
  }
  const flush = () => { const queued = [...frames.values()]; frames.clear(); queued.forEach(callback => callback()) }
  const card = target()
  const child = target()
  card.contains = element => element === child
  const elements = [card, child]
  const root = { querySelectorAll: () => elements }
  iosReveal.mounted(root)
  flush()
  assert.equal(card.animations.length, 1)
  assert.equal(card.animations[0].options.fill, 'backwards', 'delayed entry must not flash the final state before starting')
  assert.equal(child.animations.length, 0, 'nested content must not move twice')
  const row = target()
  elements.push(row)
  observer.callback()
  flush()
  observer.callback()
  flush()
  assert.equal(row.animations.length, 1)
  reduce = true
  const reducedRow = target()
  elements.push(reducedRow)
  observer.callback()
  flush()
  reduce = false
  assert.equal(reducedRow.animations.length, 0)
  observer.callback()
  iosReveal.unmounted(root)
  assert.equal(observer.disconnected, true)
  assert.equal(frames.size, 0)
  assert.equal(row.animations[0].cancelled, true)
})
