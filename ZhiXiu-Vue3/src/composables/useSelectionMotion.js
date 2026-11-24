import { nextTick, watch } from 'vue'
import { reducedMotion, springFrames } from '@/utils/ios-motion'

function layoutRect(target, root) {
  let x = target.offsetLeft
  let y = target.offsetTop
  for (let parent = target.offsetParent; parent && parent !== root; parent = parent.offsetParent) {
    x += parent.offsetLeft
    y += parent.offsetTop
  }
  return [x, y, target.offsetWidth, target.offsetHeight]
}

const formatRect = ([x, y, width, height]) => ({
  transform: `translate(${x}px, ${y}px)`, width: `${width}px`, height: `${height}px`
})

/** 同一块选中背景在菜单项之间移动，折叠/缩放时重新对齐。 */
export function useSelectionMotion(rootRef, markerRef, activeKey) {
  let animation = null
  let ready = false
  let targetRect = null

  function align(animate) {
    const root = rootRef.value
    const marker = markerRef.value
    const target = root?.querySelector('[aria-current="page"], .service-nav__more.is-active')
    if (!root || !marker) return
    if (!target) {
      animation?.cancel()
      marker.style.opacity = '0'
      root.classList.remove('has-ios-selection')
      ready = false
      return
    }
    const destination = layoutRect(target, root)
    if (targetRect && destination.every((value, index) => value === targetRect[index]) && ready) return
    let origin = destination
    if (ready && animate && !reducedMotion() && marker.animate) {
      const bounds = marker.getBoundingClientRect()
      const container = root.getBoundingClientRect()
      origin = [bounds.left - container.left + root.scrollLeft - root.clientLeft,
        bounds.top - container.top + root.scrollTop - root.clientTop, bounds.width, bounds.height]
    }
    animation?.cancel()
    Object.assign(marker.style, formatRect(destination), { opacity: '1' })
    root.classList.add('has-ios-selection')
    if (ready && animate && !reducedMotion() && marker.animate) {
      animation = marker.animate(springFrames(origin, destination, formatRect), { duration: 440, easing: 'linear' })
    }
    targetRect = destination
    ready = true
  }

  watch([rootRef, markerRef], ([root, marker], previous, onCleanup) => {
    ready = false
    targetRect = null
    if (!root || !marker) return
    const resize = () => align(false)
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(resize) : null
    observer?.observe(root)
    window.addEventListener('resize', resize)
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const syncMotion = () => { animation?.cancel(); align(false) }
    media.addEventListener('change', syncMotion)
    nextTick(resize)
    onCleanup(() => {
      observer?.disconnect()
      window.removeEventListener('resize', resize)
      media.removeEventListener('change', syncMotion)
      animation?.cancel()
      root.classList.remove('has-ios-selection')
    })
  }, { flush: 'post' })

  watch(activeKey, () => nextTick(() => align(true)), { flush: 'post' })
}
