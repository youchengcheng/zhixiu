<template>
  <!-- 萌宠小猫维修工：装饰性插画，对读屏隐藏；颜色全部走语义令牌，不硬编码色值 -->
  <svg class="auth-mascot" viewBox="0 0 220 200" aria-hidden="true">
    <defs>
      <!-- 身体渐变：主色亮部到深部 -->
      <linearGradient id="auth-mascot-body" x1="0" y1="0" x2="0" y2="1">
        <stop class="mascot-stop-light" offset="0%" />
        <stop class="mascot-stop-deep" offset="100%" />
      </linearGradient>
      <!-- 安全帽渐变 -->
      <linearGradient id="auth-mascot-hat" x1="0" y1="0" x2="0" y2="1">
        <stop class="mascot-stop-warn-light" offset="0%" />
        <stop class="mascot-stop-warn" offset="100%" />
      </linearGradient>
    </defs>

    <g class="mascot-float">
      <!-- 尾巴：末端上翘，随节奏轻摆 -->
      <path class="mascot-tail" d="M164 166q28 2 24-24" />

      <!-- 后爪 -->
      <ellipse class="mascot-paw" cx="78" cy="158" rx="16" ry="12" />
      <ellipse class="mascot-paw" cx="142" cy="158" rx="16" ry="12" />

      <!-- 手里的工具 -->
      <g class="mascot-tool">
        <rect x="150" y="146" width="17" height="12" rx="5" />
        <rect x="166" y="150" width="28" height="4" rx="2" />
      </g>

      <!-- 耳朵：从帽檐两侧探出 -->
      <path class="mascot-ear" d="M70 76 58 40l34 20z" />
      <path class="mascot-ear" d="M150 76l12-36-34 20z" />
      <path class="mascot-ear-inner" d="M74 70 66 48l20 12z" />
      <path class="mascot-ear-inner" d="M146 70l8-22-20 12z" />

      <!-- 脸 -->
      <circle class="mascot-head" cx="110" cy="104" r="52" />

      <!-- 安全帽：帽顶 + 帽檐 + 顶灯 -->
      <path class="mascot-hat" d="M68 94a42 42 0 0 1 84 0z" />
      <rect class="mascot-hat-brim" x="60" y="92" width="100" height="9" rx="4.5" />
      <circle class="mascot-lamp" cx="110" cy="58" r="6" />

      <!-- 眼睛：成组做眨眼 -->
      <g class="mascot-eyes">
        <ellipse class="mascot-eye" cx="92" cy="114" rx="9" ry="11" />
        <ellipse class="mascot-eye" cx="128" cy="114" rx="9" ry="11" />
        <circle class="mascot-pupil" cx="93" cy="116" r="4.6" />
        <circle class="mascot-pupil" cx="127" cy="116" r="4.6" />
      </g>

      <!-- 腮红 -->
      <ellipse class="mascot-blush" cx="74" cy="130" rx="9" ry="6" />
      <ellipse class="mascot-blush" cx="146" cy="130" rx="9" ry="6" />

      <!-- 鼻子与嘴 -->
      <path class="mascot-nose" d="M110 124l5 4-5 4-5-4z" />
      <path class="mascot-mouth" d="M104 136q6 6 12 0" />

      <!-- 胡须 -->
      <g class="mascot-whisker">
        <path d="M86 130 54 124" />
        <path d="M86 136 54 138" />
        <path d="M134 130 166 124" />
        <path d="M134 136 166 138" />
      </g>
    </g>

    <!-- 上浮的气泡 -->
    <circle class="mascot-bubble is-a" cx="46" cy="74" r="6" />
    <circle class="mascot-bubble is-b" cx="182" cy="88" r="5" />
    <circle class="mascot-bubble is-c" cx="172" cy="48" r="4" />
  </svg>
</template>

<style scoped>
.auth-mascot {
  display: block;
  width: 100%;
  height: auto;
  transition: transform var(--dur-fast, 200ms) var(--ease-out, ease-out);
}

/* 悬停互动：整体轻轻抬起并放大，配合内部动效形成交互反馈 */
.auth-mascot:hover {
  transform: translateY(-4px) scale(1.04);
}

/* ---------- 配色：渐变端点与图形全部使用语义令牌 ---------- */
.mascot-stop-light {
  stop-color: var(--color-primary-hover);
}

.mascot-stop-deep {
  stop-color: var(--color-primary-active);
}

.mascot-stop-warn-light {
  stop-color: var(--color-warning);
}

.mascot-stop-warn {
  stop-color: var(--color-warning-strong);
}

.mascot-head,
.mascot-ear,
.mascot-paw {
  fill: url(#auth-mascot-body);
}

.mascot-ear-inner {
  fill: var(--color-warning);
}

.mascot-tail {
  fill: none;
  stroke: var(--color-primary);
  stroke-width: 9;
  stroke-linecap: round;
}

.mascot-hat {
  fill: url(#auth-mascot-hat);
}

.mascot-hat-brim {
  fill: var(--color-warning-strong);
}

.mascot-lamp,
.mascot-eye {
  fill: var(--color-text-inverse);
}

.mascot-pupil {
  fill: var(--color-text-1);
}

.mascot-blush {
  fill: var(--color-text-inverse);
  opacity: 0.22;
}

.mascot-nose {
  fill: var(--color-warning-strong);
}

.mascot-mouth,
.mascot-whisker path {
  fill: none;
  stroke: var(--color-text-1);
  stroke-opacity: 0.5;
  stroke-width: 2.5;
  stroke-linecap: round;
}

.mascot-tool rect {
  fill: var(--color-text-inverse);
}

.mascot-bubble {
  fill: var(--color-text-inverse);
  opacity: 0;
}

/* ---------- 动效 ---------- */

/* 整体漂浮 */
.mascot-float {
  animation: mascotFloat 3.6s ease-in-out infinite;
}

@keyframes mascotFloat {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-9px);
  }
}

/* 眨眼：以眼睛自身的包围盒为中心做纵向压缩 */
.mascot-eyes {
  transform-box: fill-box;
  transform-origin: 50% 50%;
  animation: mascotBlink 4.8s ease-in-out infinite;
}

@keyframes mascotBlink {
  0%,
  90%,
  100% {
    transform: scaleY(1);
  }
  93%,
  96% {
    transform: scaleY(0.12);
  }
}

/* 帽灯呼吸 */
.mascot-lamp {
  animation: mascotLamp 1.9s ease-in-out infinite;
}

@keyframes mascotLamp {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

/* 工具轻摆：绕手握处旋转 */
.mascot-tool {
  transform-box: fill-box;
  transform-origin: 5% 95%;
  animation: mascotTool 2.6s ease-in-out infinite;
}

@keyframes mascotTool {
  0%,
  100% {
    transform: rotate(-7deg);
  }
  50% {
    transform: rotate(9deg);
  }
}

/* 尾巴轻摆：绕尾根旋转 */
.mascot-tail {
  transform-box: fill-box;
  transform-origin: 0% 100%;
  animation: mascotTail 2.8s ease-in-out infinite;
}

@keyframes mascotTail {
  0%,
  100% {
    transform: rotate(0deg);
  }
  50% {
    transform: rotate(-10deg);
  }
}

/* 气泡上浮 */
.mascot-bubble {
  animation: mascotBubble 4.4s ease-in-out infinite;
}

.mascot-bubble.is-b {
  animation-delay: 1.4s;
}

.mascot-bubble.is-c {
  animation-delay: 2.6s;
}

@keyframes mascotBubble {
  0% {
    transform: translateY(8px);
    opacity: 0;
  }
  25% {
    opacity: 0.75;
  }
  100% {
    transform: translateY(-30px);
    opacity: 0;
  }
}

/* 尊重系统「减少动态效果」偏好 */
@media (prefers-reduced-motion: reduce) {
  .auth-mascot:hover {
    transform: none;
  }

  .mascot-float,
  .mascot-eyes,
  .mascot-lamp,
  .mascot-tool,
  .mascot-tail,
  .mascot-bubble {
    animation: none;
  }

  .mascot-bubble {
    opacity: 0.5;
  }
}
</style>
