// 统一弹簧曲线：位置与速度连续，末端轻微回弹；不依赖动画库。
export function springProgress(time) {
  if (time <= 0) return 0
  if (time >= 1) return 1
  return 1 - Math.exp(-8 * time) * (Math.cos(10 * time) + 0.8 * Math.sin(10 * time))
}

export function springFrames(from, to, format, steps = 36) {
  return Array.from({ length: steps + 1 }, (_, index) => {
    const offset = index / steps
    const progress = springProgress(offset)
    const values = from.map((value, i) => value + (to[i] - value) * progress)
    return { ...format(values), offset }
  })
}

export function reducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** 按下立即收缩，松开回弹；取消触摸、切换页面与卸载均清理动画。 */
export function installPressFeedback(root) {
  let pressed = null
  const animations = new Set()

  function animate(target, frames, options) {
    const animation = target.animate(frames, options)
    animations.add(animation)
    animation.onfinish = () => { if (options.fill !== 'forwards') animations.delete(animation) }
    animation.oncancel = () => animations.delete(animation)
    return animation
  }

  function release(event) {
    if (!pressed || (event?.pointerId !== undefined && event.pointerId !== pressed.id)) return
    const { target, animation } = pressed
    pressed = null
    const transform = target.isConnected ? getComputedStyle(target).transform : 'none'
    animation.cancel()
    if (!target.isConnected || reducedMotion()) return
    const match = transform.match(/^matrix\(([^,]+)/)
    const scale = match ? Number(match[1]) : 0.965
    animate(target, springFrames([scale], [1], ([value]) => ({ transform: `scale(${value})` })), {
      duration: 360, easing: 'linear'
    })
  }

  function press(event) {
    if (event.isPrimary === false || (event.button !== undefined && event.button !== 0) || reducedMotion()) return
    const target = event.target?.closest?.('button, a, [role="button"], .el-switch')
    if (!target || !target.animate || target.matches(':disabled, [aria-disabled="true"], .is-disabled')) return
    release()
    const currentTransform = getComputedStyle(target).transform
    for (const animation of target.getAnimations?.() || []) {
      if (animations.has(animation)) animation.cancel()
    }
    const animation = animate(target, [{ transform: currentTransform === 'none' ? 'scale(1)' : currentTransform }, { transform: 'scale(0.965)' }], {
      duration: 90, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)', fill: 'forwards'
    })
    pressed = { target, animation, id: event.pointerId }
  }

  root.addEventListener('pointerdown', press, true)
  root.addEventListener('pointerup', release, true)
  root.addEventListener('pointercancel', release, true)
  root.addEventListener('visibilitychange', release)
  root.defaultView?.addEventListener('blur', release)
  return () => {
    root.removeEventListener('pointerdown', press, true)
    root.removeEventListener('pointerup', release, true)
    root.removeEventListener('pointercancel', release, true)
    root.removeEventListener('visibilitychange', release)
    root.defaultView?.removeEventListener('blur', release)
    for (const animation of animations) animation.cancel()
    animations.clear()
    pressed = null
  }
}

const revealControllers = new WeakMap()
const revealSelector = '.page-header, .home-welcome, .section-card, .stat-card, .pending-card, .notice-item, .el-table__row, .auth-story, .auth-panel'

export const iosReveal = {
  mounted(root) {
    const seen = new WeakSet()
    const animations = new Set()
    let frame = null

    function reveal() {
      frame = null
      const fresh = [...root.querySelectorAll(revealSelector)].filter(element => !seen.has(element))
      fresh.forEach(element => seen.add(element))
      if (reducedMotion() || document.hidden) return
      // 初次入场只动外层；加载完成后新出现的列表行可以独立入场。
      const targets = fresh.filter(element => !fresh.some(parent => parent !== element && parent.contains(element)))
      let index = 0
      for (const element of targets) {
        const rect = element.getBoundingClientRect()
        if (rect.bottom < 0 || rect.top > window.innerHeight || !element.animate) continue
        const animation = element.animate([
          { opacity: 0, transform: 'translateY(12px) scale(0.988)' },
          { opacity: 1, transform: 'translateY(-1px) scale(1)', offset: 0.78 },
          { opacity: 1, transform: 'translateY(0) scale(1)' }
        ], { duration: 380, delay: Math.min(index++ * 32, 128), easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)', fill: 'backwards' })
        animations.add(animation)
        animation.onfinish = () => animations.delete(animation)
        animation.oncancel = () => animations.delete(animation)
      }
    }

    function schedule() {
      if (frame === null) frame = requestAnimationFrame(reveal)
    }
    const observer = new MutationObserver(schedule)
    observer.observe(root, { childList: true, subtree: true })
    schedule()
    revealControllers.set(root, () => {
      observer.disconnect()
      if (frame !== null) cancelAnimationFrame(frame)
      for (const animation of animations) animation.cancel()
      animations.clear()
    })
  },
  unmounted(root) {
    revealControllers.get(root)?.()
    revealControllers.delete(root)
  }
}
