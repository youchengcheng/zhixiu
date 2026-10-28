// 三端路由表：菜单、面包屑、页面标题全部由 route.meta 驱动，不维护第二份配置
// meta 字段说明
//   title      页面标题（同时用于菜单文案与 document.title）
//   icon       菜单图标名，取值见 utils/menu-icons.js
//   group      菜单分组名，同组菜单会归到一个分组标题下
//   hidden     不在菜单中显示（详情页等）
//   activeMenu 详情页高亮的菜单路径
//   public     免登录访问
//   role       访问该分支所需的角色

// 两种注册身份复用同一个页面外壳，切换时只替换表单。
const registerPage = () => import('@/views/auth/RegisterPage.vue')

export const constantRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', public: true, layout: 'blank' }
  },
  {
    path: '/register',
    name: 'Register',
    component: registerPage,
    meta: { title: '业主注册', public: true, layout: 'blank' }
  },
  {
    path: '/register/worker',
    name: 'RegisterWorker',
    component: registerPage,
    meta: { title: '师傅注册', public: true, layout: 'blank' }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/Forbidden.vue'),
    meta: { title: '无访问权限', public: true, layout: 'blank' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', public: true, layout: 'blank' }
  }
]

export const ownerRoutes = {
  path: '/owner',
  component: () => import('@/layouts/OwnerLayout.vue'),
  redirect: '/owner/home',
  meta: { role: 'OWNER' },
  children: [
    {
      path: 'home',
      name: 'OwnerHome',
      component: () => import('@/views/owner/Home.vue'),
      meta: { title: '首页', icon: 'HomeFilled', group: '报修服务' }
    },
    {
      path: 'ai-assistant',
      name: 'OwnerAiAssistant',
      component: () => import('@/views/owner/AiAssistant.vue'),
      meta: { title: '智能维修助手', icon: 'ChatDotRound', group: '报修服务' }
    },
    {
      path: 'order/create',
      name: 'OwnerOrderCreate',
      component: () => import('@/views/owner/OrderCreate.vue'),
      meta: { title: '提交报修', icon: 'SetUp', group: '报修服务' }
    },
    {
      path: 'order/list',
      name: 'OwnerOrderList',
      component: () => import('@/views/owner/OrderList.vue'),
      meta: { title: '我的工单', icon: 'Tickets', group: '报修服务' }
    },
    {
      path: 'order/:id',
      name: 'OwnerOrderDetail',
      component: () => import('@/views/owner/OrderDetail.vue'),
      meta: { title: '工单详情', hidden: true, activeMenu: '/owner/order/list' }
    },
    {
      path: 'property',
      name: 'OwnerProperty',
      component: () => import('@/views/owner/Property.vue'),
      meta: { title: '我的房产', icon: 'OfficeBuilding', group: '社区与账户' }
    },
    {
      path: 'announcement',
      name: 'OwnerAnnouncement',
      component: () => import('@/views/owner/Announcement.vue'),
      meta: { title: '物业公告', icon: 'Bell', group: '社区与账户' }
    },
    {
      path: 'feedback',
      name: 'OwnerFeedback',
      component: () => import('@/views/owner/Feedback.vue'),
      meta: { title: '问题反馈', icon: 'ChatLineSquare', group: '社区与账户' }
    },
    {
      path: 'profile',
      name: 'OwnerProfile',
      component: () => import('@/views/owner/Profile.vue'),
      meta: { title: '个人中心', icon: 'User', group: '社区与账户' }
    }
  ]
}

export const workerRoutes = {
  path: '/worker',
  component: () => import('@/layouts/WorkerLayout.vue'),
  redirect: '/worker/workbench',
  meta: { role: 'WORKER' },
  children: [
    {
      path: 'workbench',
      name: 'WorkerWorkbench',
      component: () => import('@/views/worker/Workbench.vue'),
      meta: { title: '工作台', icon: 'Odometer', group: '接单作业' }
    },
    {
      path: 'pending',
      name: 'WorkerPending',
      component: () => import('@/views/worker/Pending.vue'),
      meta: { title: '待接单', icon: 'Aim', group: '接单作业' }
    },
    {
      path: 'order/list',
      name: 'WorkerOrderList',
      component: () => import('@/views/worker/OrderList.vue'),
      meta: { title: '我的工单', icon: 'Tickets', group: '接单作业' }
    },
    {
      path: 'order/:id',
      name: 'WorkerOrderDetail',
      component: () => import('@/views/worker/OrderDetail.vue'),
      meta: { title: '处理工单', hidden: true, activeMenu: '/worker/order/list' }
    },
    {
      path: 'statistics',
      name: 'WorkerStatistics',
      component: () => import('@/views/worker/Statistics.vue'),
      meta: { title: '个人统计', icon: 'DataAnalysis', group: '接单作业' }
    },
    {
      path: 'announcement',
      name: 'WorkerAnnouncement',
      component: () => import('@/views/worker/Announcement.vue'),
      meta: { title: '物业公告', icon: 'Bell', group: '账户' }
    },
    {
      path: 'profile',
      name: 'WorkerProfile',
      component: () => import('@/views/worker/Profile.vue'),
      meta: { title: '个人中心', icon: 'User', group: '账户' }
    }
  ]
}

export const adminRoutes = {
  path: '/admin',
  component: () => import('@/layouts/AdminLayout.vue'),
  redirect: '/admin/dashboard',
  meta: { role: 'ADMIN' },
  children: [
    {
      path: 'dashboard',
      name: 'AdminDashboard',
      component: () => import('@/views/admin/Dashboard.vue'),
      meta: { title: '统计看板', icon: 'DataAnalysis' }
    },
    {
      path: 'order/list',
      name: 'AdminOrderList',
      component: () => import('@/views/admin/OrderList.vue'),
      meta: { title: '工单管理', icon: 'Tickets' }
    },
    {
      path: 'order/:id',
      name: 'AdminOrderDetail',
      component: () => import('@/views/admin/OrderDetail.vue'),
      meta: { title: '工单详情', hidden: true, activeMenu: '/admin/order/list' }
    },
    {
      path: 'worker',
      name: 'AdminWorker',
      component: () => import('@/views/admin/Worker.vue'),
      meta: { title: '师傅管理', icon: 'Tools', group: '人员管理' }
    },
    {
      path: 'owner',
      name: 'AdminOwner',
      component: () => import('@/views/admin/Owner.vue'),
      meta: { title: '业主管理', icon: 'UserFilled', group: '人员管理' }
    },
    {
      path: 'sla',
      name: 'AdminSla',
      component: () => import('@/views/admin/Sla.vue'),
      meta: { title: 'SLA 配置', icon: 'Stopwatch', group: '配置与知识' }
    },
    {
      path: 'knowledge',
      name: 'AdminKnowledge',
      component: () => import('@/views/admin/Knowledge.vue'),
      meta: { title: '知识库', icon: 'Collection', group: '配置与知识' }
    },
    {
      path: 'announcement',
      name: 'AdminAnnouncement',
      component: () => import('@/views/admin/Announcement.vue'),
      meta: { title: '公告管理', icon: 'Bell', group: '内容与反馈' }
    },
    {
      path: 'feedback',
      name: 'AdminFeedback',
      component: () => import('@/views/admin/Feedback.vue'),
      meta: { title: '反馈管理', icon: 'ChatLineSquare', group: '内容与反馈' }
    },
    {
      path: 'log',
      name: 'AdminLog',
      component: () => import('@/views/admin/Log.vue'),
      meta: { title: '操作日志', icon: 'Document', group: '系统' }
    },
    {
      path: 'profile',
      name: 'AdminProfile',
      component: () => import('@/views/admin/Profile.vue'),
      meta: { title: '个人中心', icon: 'User', group: '系统' }
    }
  ]
}

// 角色 → 布局路由，供守卫与菜单读取
export const roleRoutes = {
  OWNER: ownerRoutes,
  WORKER: workerRoutes,
  ADMIN: adminRoutes
}

export const whiteList = ['/login', '/register', '/register/worker', '/403', '/404']
