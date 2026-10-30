(() => {
  "use strict";

  const state = {
    admin: null,
    csrf: null,
    route: "dashboard",
    summary: null,
    accounts: [],
    courses: [],
    filters: { accountQuery: "", accountRole: "", courseQuery: "", courseStatus: "" },
    modal: null,
    modalDirty: false,
    modalConfirm: false,
    detailCourseId: null,
    detailTab: "members",
    studentDetailId: null,
    loading: false,
    timer: null,
  };

  const $ = (selector) => document.querySelector(selector);
  const content = () => $("#content");
  const esc = (value) => String(value ?? "")
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;").replaceAll("'", "&#039;");
  const fmt = (value) => value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";
  const fmtDate = (value) => value ? new Date(value).toLocaleDateString("zh-CN") : "—";

  async function api(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = { ...(options.headers || {}) };
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    if (method !== "GET" && method !== "HEAD" && state.csrf) headers["X-CSRF-Token"] = state.csrf;
    let response;
    try {
      response = await fetch(path, {
        ...options,
        method,
        headers,
        credentials: "same-origin",
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      });
    } catch (_) {
      throw new Error("网络连接失败，请检查网络后重试");
    }
    let payload = null;
    try { payload = await response.json(); } catch (_) { /* empty response */ }
    if (response.status === 401) {
      redirectToLogin();
      throw new Error(payload?.message || "登录已失效");
    }
    if (!response.ok) {
      const error = new Error(payload?.message || `请求失败 (${response.status})`);
      error.status = response.status;
      error.code = payload?.code;
      throw error;
    }
    return payload?.data;
  }

  function redirectToLogin() {
    state.admin = null;
    state.csrf = null;
    if (state.timer) clearInterval(state.timer);
    state.timer = null;
    window.location.replace("/admin/login");
  }

  async function showApp(session) {
    state.admin = session.admin;
    state.csrf = session.csrfToken;
    $("#app").hidden = false;
    $("#admin-meta").innerHTML = `${esc(state.admin.name)}<br><span style="opacity:.55">${esc(state.admin.username)}</span>`;
    setRoute("dashboard");
    if (state.timer) clearInterval(state.timer);
    state.timer = setInterval(autoRefresh, 5000);
  }

  async function boot() {
    try {
      const session = await api("/api/admin/auth/session");
      await showApp(session);
    } catch (_) {
      redirectToLogin();
    }
  }

  function setRoute(route) {
    state.route = route;
    closeModal();
    document.querySelectorAll(".nav-item").forEach((item) => {
      item.classList.toggle("active", item.dataset.route === route);
    });
    const titles = {
      dashboard: ["OVERVIEW", "总览", "账号、课程与课堂状态的集中视图"],
      accounts: ["ACCOUNTS", "账号管理", "教师与学生账号、密码与成员关系"],
      courses: ["COURSES", "课程管理", "课程状态、任课教师与教学数据"],
    };
    const [kicker, title, sub] = titles[route] || titles.dashboard;
    $("#page-kicker").textContent = kicker;
    $("#page-title").textContent = title;
    $("#page-sub").textContent = sub;
    refreshRoute(true);
  }

  async function refreshRoute(manual = false) {
    if (state.loading) return;
    if (!manual && isEditing()) return;
    state.loading = true;
    $("#sync-state").textContent = "同步中…";
    try {
      if (state.modal?.kind === "courseDetail" && state.detailCourseId) {
        await loadCourseDetail(state.detailCourseId, true);
      } else if (state.modal?.kind === "studentDetail" && state.studentDetailId) {
        await loadStudentDetail(state.studentDetailId, true);
      } else if (state.route === "dashboard") {
        await loadDashboard();
      } else if (state.route === "accounts") {
        await loadAccounts();
      } else if (state.route === "courses") {
        await loadCourses();
      }
      $("#sync-state").textContent = "自动刷新 · 5s";
    } catch (error) {
      if (error.status !== 401) toast(error.message, true);
    } finally {
      state.loading = false;
    }
  }

  async function autoRefresh() {
    if (document.visibilityState !== "visible" || isEditing() || state.modalConfirm) return;
    await refreshRoute(false);
  }

  function isEditing() {
    const active = document.activeElement;
    const inField = active && (active.matches("input, textarea, select") || active.isContentEditable);
    return Boolean(state.modalDirty || inField);
  }

  async function loadDashboard() {
    state.summary = await api("/api/admin/summary");
    content().innerHTML = `
      <div class="cards">
        ${stat("教师账号", state.summary.teachers, "可任课与建课", "accent")}
        ${stat("学生账号", state.summary.students, "可加入课程")}
        ${stat("课程总数", state.summary.courses, "包含归档与软删除")}
        ${stat("归档课程", state.summary.archivedCourses, "只读课程")}
        ${stat("软删除课程", state.summary.deletedCourses, "保留历史，可永久删除")}
      </div>
      <div class="panel">
        <div class="panel-head"><h2>管理入口</h2><span class="muted">只读教学数据</span></div>
        <div class="panel-body">
          <div class="detail-grid">
            <button class="mini-card" style="text-align:left;border:1px solid var(--line)" onclick="setRoute('accounts')">
              <div class="k">账号管理</div><div class="v">教师 / 学生</div><div class="muted">搜索、创建、编辑、重置密码与删除</div>
            </button>
            <button class="mini-card" style="text-align:left;border:1px solid var(--line)" onclick="setRoute('courses')">
              <div class="k">课程管理</div><div class="v">状态与成员</div><div class="muted">归档、邀请码、成员与只读教学数据</div>
            </button>
          </div>
          <p class="form-note">管理员不代替教师编辑题目、提前截止题目或修改评分。所有教学内容以只读方式展示。</p>
        </div>
      </div>`;
  }

  function stat(label, value, hint, className = "") {
    return `<div class="stat-card ${className}"><div class="label">${esc(label)}</div><div class="value">${esc(value)}</div><div class="hint">${esc(hint)}</div></div>`;
  }

  async function loadAccounts() {
    const params = new URLSearchParams();
    if (state.filters.accountQuery) params.set("query", state.filters.accountQuery);
    if (state.filters.accountRole) params.set("role", state.filters.accountRole);
    state.accounts = await api(`/api/admin/accounts?${params}`);
    renderAccounts();
  }

  function renderAccounts() {
    const rows = state.accounts.map((account) => `
      <tr>
        <td><div class="strong">${esc(account.username)}</div><div class="sub">${esc(account.name)}</div></td>
        <td><span class="pill ${account.role === "TEACHER" ? "" : "muted"}">${account.role === "TEACHER" ? "教师" : "学生"}</span></td>
        <td>${account.courseCount}</td>
        <td>${fmt(account.createdAt)}</td>
        <td class="actions">
          <button class="btn btn-sm" onclick="openAccountForm(${account.id})">编辑</button>
          <button class="btn btn-sm" onclick="resetAccountPassword(${account.id})">重置密码</button>
          ${account.role === "STUDENT" ? `<button class="btn btn-sm" onclick="openStudentDetail(${account.id})">成绩</button>` : ""}
          <button class="btn btn-sm btn-danger" onclick="confirmDeleteAccount(${account.id}, ${account.version})">删除</button>
        </td>
      </tr>`).join("");
    content().innerHTML = `
      <div class="toolbar">
        <input class="grow" id="account-query" placeholder="搜索账号或姓名" value="${esc(state.filters.accountQuery)}" onkeydown="accountSearch(event)">
        <select id="account-role" onchange="accountRoleChanged(this.value)">
          <option value="">全部角色</option>
          <option value="TEACHER" ${state.filters.accountRole === "TEACHER" ? "selected" : ""}>教师</option>
          <option value="STUDENT" ${state.filters.accountRole === "STUDENT" ? "selected" : ""}>学生</option>
        </select>
        <button class="btn btn-primary" onclick="openAccountForm(null)">新建账号</button>
        <button class="btn" onclick="loadAccounts()">查询</button>
      </div>
      <div class="panel">
        <div class="panel-head"><h2>账号列表</h2><span class="muted">${state.accounts.length} 个账号</span></div>
        <div class="table-wrap"><table>
          <thead><tr><th>账号 / 姓名</th><th>角色</th><th>课程数</th><th>创建时间</th><th></th></tr></thead>
          <tbody>${rows || `<tr><td colspan="5"><div class="empty">没有匹配的账号</div></td></tr>`}</tbody>
        </table></div>
      </div>`;
  }

  function accountSearch(event) {
    if (event.key === "Enter") {
      state.filters.accountQuery = event.target.value.trim();
      loadAccounts();
    }
  }
  function accountRoleChanged(value) {
    state.filters.accountRole = value;
    loadAccounts();
  }

  async function loadCourses() {
    const params = new URLSearchParams();
    if (state.filters.courseQuery) params.set("query", state.filters.courseQuery);
    if (state.filters.courseStatus) params.set("status", state.filters.courseStatus);
    state.courses = await api(`/api/admin/courses?${params}`);
    renderCourses();
  }

  function courseState(course) {
    if (course.deleted) return `<span class="pill danger">已删除</span>`;
    if (course.archived) return `<span class="pill warn">已归档</span>`;
    return `<span class="pill">正常</span>`;
  }

  function renderCourses() {
    const rows = state.courses.map((course) => `
      <tr>
        <td><div class="strong">${esc(course.name)}</div><div class="sub">#${course.id} · ${esc(course.teacherName)}</div></td>
        <td>${courseState(course)}</td>
        <td>${course.memberCount}</td>
        <td>${course.questionCount} / ${course.assignmentCount}</td>
        <td><span class="pill muted">${esc(course.inviteCode)}</span></td>
        <td class="actions">
          <button class="btn btn-sm" onclick="openCourseDetail(${course.id})">详情</button>
          ${course.deleted ? `<button class="btn btn-sm btn-danger" onclick="openPermanentDelete(${course.id}, ${course.version})">永久删除</button>` :
            `<button class="btn btn-sm" onclick="openCourseForm(${course.id})">编辑</button>
             <button class="btn btn-sm" onclick="toggleArchive(${course.id}, ${course.version}, ${course.archived})">${course.archived ? "取消归档" : "归档"}</button>
             <button class="btn btn-sm" onclick="confirmResetInvite(${course.id}, ${course.version})">重置邀请码</button>
             <button class="btn btn-sm btn-danger" onclick="confirmSoftDelete(${course.id}, ${course.version})">删除</button>`}
        </td>
      </tr>`).join("");
    content().innerHTML = `
      <div class="toolbar">
        <input class="grow" id="course-query" placeholder="搜索课程或任课教师" value="${esc(state.filters.courseQuery)}" onkeydown="courseSearch(event)">
        <select onchange="courseStatusChanged(this.value)">
          <option value="">全部状态</option>
          <option value="normal" ${state.filters.courseStatus === "normal" ? "selected" : ""}>正常</option>
          <option value="archived" ${state.filters.courseStatus === "archived" ? "selected" : ""}>已归档</option>
          <option value="deleted" ${state.filters.courseStatus === "deleted" ? "selected" : ""}>已删除</option>
        </select>
        <button class="btn btn-primary" onclick="openCourseForm(null)">新建课程</button>
        <button class="btn" onclick="loadCourses()">查询</button>
      </div>
      <div class="panel">
        <div class="panel-head"><h2>课程列表</h2><span class="muted">${state.courses.length} 门课程</span></div>
        <div class="table-wrap"><table>
          <thead><tr><th>课程 / 教师</th><th>状态</th><th>成员</th><th>题目 / 作业</th><th>邀请码</th><th></th></tr></thead>
          <tbody>${rows || `<tr><td colspan="6"><div class="empty">没有匹配的课程</div></td></tr>`}</tbody>
        </table></div>
      </div>`;
  }

  function courseSearch(event) {
    if (event.key === "Enter") {
      state.filters.courseQuery = event.target.value.trim();
      loadCourses();
    }
  }
  function courseStatusChanged(value) {
    state.filters.courseStatus = value;
    loadCourses();
  }

  function openModal({ title, kicker = "", body, footer = "", wide = false, kind = "", confirm = false }) {
    state.modal = { kind };
    state.modalDirty = false;
    state.modalConfirm = confirm;
    $("#modal-root").innerHTML = `
      <div class="modal-backdrop" onclick="if(event.target===this) closeModal()">
        <div class="modal ${wide ? "wide" : ""}" role="dialog" aria-modal="true">
          <div class="modal-head"><div><div class="eyebrow">${esc(kicker)}</div><h3>${esc(title)}</h3></div><button class="modal-close" onclick="closeModal()">×</button></div>
          <div class="modal-body">${body}</div>
          <div class="modal-foot">${footer}</div>
        </div>
      </div>`;
  }

  function closeModal() {
    state.modal = null;
    state.modalDirty = false;
    state.modalConfirm = false;
    state.detailCourseId = null;
    state.studentDetailId = null;
    $("#modal-root").innerHTML = "";
  }

  function markDirty() { state.modalDirty = true; }

  function toast(message, error = false) {
    const node = $("#toast");
    node.textContent = message;
    node.className = `toast ${error ? "error" : ""}`;
    node.hidden = false;
    clearTimeout(node._timer);
    node._timer = setTimeout(() => { node.hidden = true; }, 3200);
  }

  async function openAccountForm(accountId) {
    let account = accountId ? state.accounts.find((item) => item.id === accountId) : null;
    const editing = Boolean(account);
    openModal({
      title: editing ? "编辑账号" : "新建账号",
      kicker: "ACCOUNT",
      kind: "accountForm",
      body: `
        <form id="account-form" class="form-grid" onsubmit="submitAccountForm(event, ${accountId || "null"}, ${editing ? account.version : "null"})">
          <label>账号<input id="account-username" value="${esc(account?.username || "")}" ${editing ? "disabled" : ""} required></label>
          <label>姓名<input id="account-name" value="${esc(account?.name || "")}" required></label>
          ${editing ? "" : `<label>角色<select id="account-new-role"><option value="STUDENT">学生</option><option value="TEACHER">教师</option></select></label>
          <label>初始密码<input id="account-password" type="password" minlength="8" required></label>`}
          <div class="full form-note">${editing ? "编辑姓名会校验当前版本，若列表已被其他人刷新会提示重新核对。" : "创建后可通过账号列表重置密码。所有账号密码使用 BCrypt 存储。"}</div>
        </form>`,
      footer: `<button class="btn" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="document.getElementById('account-form').requestSubmit()">${editing ? "保存" : "创建账号"}</button>`,
    });
  }

  async function submitAccountForm(event, accountId, version) {
    event.preventDefault();
    const editing = accountId !== null;
    try {
      if (editing) {
        await api(`/api/admin/accounts/${accountId}`, {
          method: "PUT",
          body: { name: $("#account-name").value.trim(), expectedVersion: version },
        });
      } else {
        await api("/api/admin/accounts", {
          method: "POST",
          body: {
            username: $("#account-username").value.trim(),
            name: $("#account-name").value.trim(),
            password: $("#account-password").value,
            role: $("#account-new-role").value,
          },
        });
      }
      closeModal();
      toast(editing ? "账号已更新" : "账号已创建");
      await loadAccounts();
    } catch (error) {
      handleActionError(error);
    }
  }

  async function resetAccountPassword(accountId) {
    try {
      const result = await api(`/api/admin/accounts/${accountId}/reset-password`, { method: "POST" });
      openModal({
        title: "临时密码",
        kicker: "CREDENTIALS",
        kind: "notice",
        confirm: true,
        body: `<p>账号 <strong>${esc(result.username)}</strong> 的旧令牌已全部失效。</p><div class="mini-card"><div class="k">临时密码</div><div class="v" style="font-family:monospace;font-size:16px">${esc(result.temporaryPassword)}</div></div><p class="form-note">请通过安全渠道交给用户，并要求登录后立即修改密码。</p>`,
        footer: `<button class="btn btn-primary" onclick="closeModal()">知道了</button>`,
      });
    } catch (error) { handleActionError(error); }
  }

  async function confirmDeleteAccount(accountId, version) {
    try {
      const impact = await api(`/api/admin/accounts/${accountId}/impact`);
      const teacher = impact.role === "TEACHER";
      openModal({
        title: teacher ? "删除教师账号？" : "删除学生账号？",
        kicker: "DELETE ACCOUNT",
        kind: "confirm",
        confirm: true,
        body: `<p>账号 <strong>${esc(impact.username)}</strong> 将被永久删除，旧令牌立即失效。</p>
          <div class="detail-grid">
            <div class="mini-card"><div class="k">${teacher ? "拥有课程" : "加入课程"}</div><div class="v">${teacher ? impact.ownedCourseCount : impact.membershipCount}</div></div>
            <div class="mini-card"><div class="k">教学记录</div><div class="v">${impact.answerCount + impact.attendanceCount + impact.submissionCount}</div></div>
          </div>
          <p class="form-note">${teacher ? "将级联删除其全部课程、成员关系、题目、作业、评分、签到记录和上传图片。" : "将清理其成员关系、个人答题、签到和作业提交及图片。课程本身保留。"} 此操作不可撤销。</p>`,
        footer: `<button class="btn" onclick="closeModal()">取消</button><button class="btn btn-danger" onclick="deleteAccount(${accountId}, ${version})">确认永久删除</button>`,
      });
    } catch (error) { handleActionError(error); }
  }

  async function deleteAccount(accountId, version) {
    try {
      await api(`/api/admin/accounts/${accountId}?expectedVersion=${version}&confirm=true`, { method: "DELETE" });
      closeModal();
      toast("账号已永久删除");
      await loadAccounts();
    } catch (error) { handleActionError(error); }
  }

  async function openCourseForm(courseId) {
    const teachers = await api("/api/admin/accounts?role=TEACHER");
    const course = courseId ? state.courses.find((item) => item.id === courseId) : null;
    if (!teachers.length) {
      toast("请先创建教师账号", true);
      return;
    }
    const options = teachers.map((teacher) => `<option value="${teacher.id}" ${course?.teacherId === teacher.id ? "selected" : ""}>${esc(teacher.name)} (${esc(teacher.username)})</option>`).join("");
    openModal({
      title: course ? "编辑课程" : "新建课程",
      kicker: "COURSE",
      kind: "courseForm",
      body: `<form id="course-form" class="form-grid" onsubmit="submitCourseForm(event, ${courseId || "null"}, ${course ? course.version : "null"})">
        <label class="full">课程名称<input id="course-name" value="${esc(course?.name || "")}" required></label>
        <label class="full">课程简介<textarea id="course-description" required>${esc(course?.description || "")}</textarea></label>
        <label>任课教师<select id="course-teacher">${options}</select></label>
        <div class="form-note full">${course ? "修改课程会校验版本，列表刷新后若版本变化会要求重新核对。" : "邀请码由服务端自动生成，可在课程列表重置。"}</div>
      </form>`,
      footer: `<button class="btn" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="document.getElementById('course-form').requestSubmit()">${course ? "保存" : "创建课程"}</button>`,
    });
  }

  async function submitCourseForm(event, courseId, version) {
    event.preventDefault();
    try {
      if (courseId !== null) {
        await api(`/api/admin/courses/${courseId}`, {
          method: "PUT",
          body: {
            name: $("#course-name").value.trim(),
            description: $("#course-description").value.trim(),
            teacherId: Number($("#course-teacher").value),
            expectedVersion: version,
          },
        });
      } else {
        await api("/api/admin/courses", {
          method: "POST",
          body: {
            name: $("#course-name").value.trim(),
            description: $("#course-description").value.trim(),
            teacherId: Number($("#course-teacher").value),
          },
        });
      }
      closeModal();
      toast(courseId ? "课程已更新" : "课程已创建");
      await loadCourses();
    } catch (error) { handleActionError(error); }
  }

  async function toggleArchive(courseId, version, archived) {
    try {
      await api(`/api/admin/courses/${courseId}/${archived ? "unarchive" : "archive"}?expectedVersion=${version}`, { method: "POST" });
      toast(archived ? "课程已取消归档" : "课程已归档");
      await loadCourses();
    } catch (error) { handleActionError(error); }
  }

  async function confirmResetInvite(courseId, version) {
    openModal({
      title: "重置邀请码？",
      kicker: "INVITE CODE",
      kind: "confirm",
      confirm: true,
      body: `<p>当前邀请码将立即失效，已加入成员不受影响。</p><p class="form-note">重置后请重新向学生发布新的邀请码。</p>`,
      footer: `<button class="btn" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="resetInvite(${courseId}, ${version})">确认重置</button>`,
    });
  }

  async function resetInvite(courseId, version) {
    try {
      const course = await api(`/api/admin/courses/${courseId}/invite-code/reset?expectedVersion=${version}`, { method: "POST" });
      closeModal();
      toast(`邀请码已重置为 ${course.inviteCode}`);
      await loadCourses();
    } catch (error) { handleActionError(error); }
  }

  function confirmSoftDelete(courseId, version) {
    openModal({
      title: "删除课程？",
      kicker: "SOFT DELETE",
      kind: "confirm",
      confirm: true,
      body: `<p>课程会从正常列表隐藏，但成员、题目、作业、成绩和图片会保留。</p><p class="form-note">之后可在“已删除”筛选中查看并永久删除。此操作不会立即清理数据库。</p>`,
      footer: `<button class="btn" onclick="closeModal()">取消</button><button class="btn btn-danger" onclick="softDeleteCourse(${courseId}, ${version})">确认删除</button>`,
    });
  }

  async function softDeleteCourse(courseId, version) {
    try {
      await api(`/api/admin/courses/${courseId}?expectedVersion=${version}`, { method: "DELETE" });
      closeModal();
      toast("课程已软删除，可在已删除筛选中永久删除");
      await loadCourses();
    } catch (error) { handleActionError(error); }
  }

  async function openPermanentDelete(courseId, version) {
    try {
      const impact = await api(`/api/admin/courses/${courseId}/impact`);
      const course = state.courses.find((item) => item.id === courseId);
      openModal({
        title: "永久删除课程",
        kicker: "PERMANENT DELETE",
        kind: "confirm",
        confirm: true,
        body: `<p>这会永久清理课程及其全部教学数据，且不可恢复。</p>
          <div class="detail-grid">
            <div class="mini-card"><div class="k">成员</div><div class="v">${impact.members}</div></div>
            <div class="mini-card"><div class="k">题目 / 作答</div><div class="v">${impact.questions} / ${impact.answers}</div></div>
            <div class="mini-card"><div class="k">作业 / 提交</div><div class="v">${impact.assignments} / ${impact.submissions}</div></div>
            <div class="mini-card"><div class="k">签到 / 记录</div><div class="v">${impact.attendanceSessions} / ${impact.attendanceRecords}</div></div>
          </div>
          <label style="display:grid;gap:6px;margin-top:16px">输入课程名称确认<input id="purge-confirm-name" placeholder="${esc(course?.name || "")}" oninput="markDirty()"></label>
          <p class="form-note">上传的作业图片会在数据库事务提交后删除。若事务失败，数据库和文件会保留。</p>`,
        footer: `<button class="btn" onclick="closeModal()">取消</button><button class="btn btn-danger" onclick="permanentDelete(${courseId}, ${version})">永久删除</button>`,
      });
    } catch (error) { handleActionError(error); }
  }

  async function permanentDelete(courseId, version) {
    const name = $("#purge-confirm-name").value.trim();
    if (!name) { toast("请输入课程名称确认", true); return; }
    try {
      await api(`/api/admin/courses/${courseId}/permanent?expectedVersion=${version}&confirmName=${encodeURIComponent(name)}`, { method: "DELETE" });
      closeModal();
      toast("课程及上传文件已永久删除");
      await loadCourses();
    } catch (error) { handleActionError(error); }
  }

  async function openCourseDetail(courseId, quiet = false) {
    try {
      const detail = await api(`/api/admin/courses/${courseId}/detail`);
      state.detailCourseId = courseId;
      const currentTab = state.detailTab || "members";
      const body = courseDetailBody(detail, currentTab);
      if (quiet) {
        const modal = document.querySelector(".modal");
        if (modal) {
          modal.querySelector(".modal-head h3").textContent = detail.course.name;
          modal.querySelector(".modal-body").innerHTML = body;
          return;
        }
      }
      openModal({
        title: detail.course.name,
        kicker: `COURSE #${detail.course.id}`,
        kind: "courseDetail",
        wide: true,
        body,
        footer: `<button class="btn" onclick="closeModal()">关闭</button>`,
      });
    } catch (error) { handleActionError(error); }
  }

  function courseDetailBody(detail, tab) {
    const c = detail.course;
    const tabs = `<div class="detail-tabs">
      ${detailTab("members", "成员", tab)}
      ${detailTab("questions", "题目", tab)}
      ${detailTab("assignments", "作业与评分", tab)}
    </div>`;
    let body = "";
    if (tab === "members") {
      body = `
        <form class="toolbar" onsubmit="addMember(event, ${c.id})">
          <input class="grow" id="member-no" placeholder="输入学生账号添加成员" oninput="markDirty()">
          <button class="btn btn-primary" type="submit">添加成员</button>
        </form>
        <div class="hint">移除成员会保留其历史签到、答题和作业记录，但之后不再计入统计。</div>
        <div style="margin-top:10px">${detail.members.map((member) => `
          <div class="list-row"><div class="left"><div class="title">${esc(member.name)}</div><div class="meta">${esc(member.studentNo)} · 加入于 ${fmtDate(member.joinedAt)}</div></div>
          <button class="btn btn-sm btn-danger" onclick="removeMember(${c.id}, ${member.studentId}, '${esc(member.name)}')">移除</button></div>`).join("") || `<div class="empty">暂无成员</div>`}</div>`;
    } else if (tab === "questions") {
      body = detail.questions.map((q) => `
        <div class="list-row"><div class="left"><div class="title">${esc(q.prompt)}</div><div class="meta">${q.type} · 截止 ${fmt(q.deadlineAt)} · 提交 ${q.submissionCount} · 正确 ${q.correctCount}</div></div></div>`).join("") || `<div class="empty">暂无题目</div>`;
    } else {
      body = detail.assignments.map((a) => `
        <div class="panel" style="margin-top:10px"><div class="panel-head"><h2>${esc(a.title)}</h2><span class="muted">${a.submissionCount} 份提交</span></div>
          <div class="panel-body"><p class="muted">${esc(a.description)}</p><p class="sub">截止 ${fmt(a.deadlineAt)}</p>
          ${a.submissions.map((s) => `<div class="list-row"><div class="left"><div class="title">${esc(s.studentName)} <span class="sub">${esc(s.studentNo)}</span></div><div class="meta">${fmt(s.updatedAt)} · ${esc(s.note || "无提交说明")}</div></div><div style="text-align:right"><span class="pill ${s.score == null ? "muted" : ""}">${s.score == null ? "未评分" : `${s.score} 分`}</span><div class="sub">${esc(s.feedback || "")}</div></div></div>`).join("") || `<div class="empty">暂无提交</div>`}</div></div>`).join("") || `<div class="empty">暂无作业</div>`;
    }
    return `<div class="detail-grid" style="margin-bottom:14px">
        <div class="mini-card"><div class="k">成员 / 状态</div><div class="v">${c.memberCount} <span class="sub">${c.deleted ? "已删除" : c.archived ? "已归档" : "正常"}</span></div></div>
        <div class="mini-card"><div class="k">题目 / 作业</div><div class="v">${c.questionCount} / ${c.assignmentCount}</div></div>
      </div>
      <p class="form-note">教学数据只读。管理员不修改题目、评分或反馈。</p>${tabs}${body}`;
  }

  function detailTab(key, label, current) {
    return `<button class="detail-tab ${key === current ? "active" : ""}" onclick="switchDetailTab('${key}')">${label}</button>`;
  }

  function switchDetailTab(tab) {
    state.detailTab = tab;
    state.modalDirty = false;
    openCourseDetail(state.detailCourseId, false);
  }

  async function addMember(event, courseId) {
    event.preventDefault();
    const studentNo = $("#member-no").value.trim();
    if (!studentNo) return;
    try {
      await api(`/api/admin/courses/${courseId}/members`, { method: "POST", body: { studentNo } });
      state.modalDirty = false;
      toast("成员已添加");
      await openCourseDetail(courseId);
    } catch (error) { handleActionError(error); }
  }

  function removeMember(courseId, studentId, name) {
    openModal({
      title: "移除成员？",
      kicker: "MEMBERSHIP",
      kind: "confirm",
      confirm: true,
      body: `<p>将 ${esc(name)} 从课程成员中移除。</p><p class="form-note">历史签到、答题、作业和评分会保留，但不再计入课程统计。学生之后可凭邀请码重新加入。</p>`,
      footer: `<button class="btn" onclick="closeModal()">取消</button><button class="btn btn-danger" onclick="doRemoveMember(${courseId}, ${studentId})">确认移除</button>`,
    });
  }

  async function doRemoveMember(courseId, studentId) {
    try {
      await api(`/api/admin/courses/${courseId}/members/${studentId}`, { method: "DELETE" });
      closeModal();
      toast("成员已移除");
      await openCourseDetail(courseId);
    } catch (error) { handleActionError(error); }
  }

  async function openStudentDetail(accountId, quiet = false) {
    try {
      const detail = await api(`/api/admin/accounts/${accountId}/student-detail`);
      state.studentDetailId = accountId;
      const body = studentDetailBody(detail);
      if (quiet) {
        const modal = document.querySelector(".modal");
        if (modal) { modal.querySelector(".modal-body").innerHTML = body; return; }
      }
      openModal({
        title: `${detail.account.name} 的成绩`,
        kicker: `STUDENT · ${detail.account.username}`,
        kind: "studentDetail",
        wide: true,
        body,
        footer: `<button class="btn" onclick="closeModal()">关闭</button>`,
      });
    } catch (error) { handleActionError(error); }
  }

  function studentDetailBody(detail) {
    if (!detail.courses.length) return `<div class="empty">该学生尚未加入课程</div>`;
    return detail.courses.map((course) => `
      <div class="panel" style="margin-top:12px"><div class="panel-head"><h2>${esc(course.courseName)}</h2><span class="muted">只读成绩</span></div>
      <div class="panel-body">
        <div class="detail-grid">
          <div class="mini-card"><div class="k">签到</div><div class="v">${course.attendanceCount}/${course.attendanceTotal}</div></div>
          <div class="mini-card"><div class="k">答题正确</div><div class="v">${course.correctCount}/${course.answeredCount}</div></div>
          <div class="mini-card"><div class="k">作业提交</div><div class="v">${course.submittedCount}/${course.assignmentTotal}</div></div>
          <div class="mini-card"><div class="k">评分均值</div><div class="v">${course.averageScore == null ? "—" : course.averageScore.toFixed(1)}</div></div>
        </div>
        ${course.assignments.filter((a) => a.feedback).map((a) => `<p class="sub">${esc(a.title)} 评语：${esc(a.feedback)}</p>`).join("")}
      </div></div>`).join("");
  }

  function handleActionError(error) {
    if (error.status === 409 || error.code === "VERSION_CONFLICT" || error.code === "SUBMISSION_CHANGED") {
      toast("数据已被其他人修改，请刷新后重新核对", true);
      closeModal();
      refreshRoute(true);
    } else {
      toast(error.message, true);
    }
  }

  window.setRoute = setRoute;
  window.refreshRoute = refreshRoute;
  window.loadAccounts = loadAccounts;
  window.loadCourses = loadCourses;
  window.accountSearch = accountSearch;
  window.accountRoleChanged = accountRoleChanged;
  window.courseSearch = courseSearch;
  window.courseStatusChanged = courseStatusChanged;
  window.openAccountForm = openAccountForm;
  window.submitAccountForm = submitAccountForm;
  window.resetAccountPassword = resetAccountPassword;
  window.confirmDeleteAccount = confirmDeleteAccount;
  window.deleteAccount = deleteAccount;
  window.openStudentDetail = openStudentDetail;
  window.openCourseForm = openCourseForm;
  window.submitCourseForm = submitCourseForm;
  window.toggleArchive = toggleArchive;
  window.confirmResetInvite = confirmResetInvite;
  window.resetInvite = resetInvite;
  window.confirmSoftDelete = confirmSoftDelete;
  window.softDeleteCourse = softDeleteCourse;
  window.openPermanentDelete = openPermanentDelete;
  window.permanentDelete = permanentDelete;
  window.openCourseDetail = openCourseDetail;
  window.closeModal = closeModal;
  window.markDirty = markDirty;
  window.switchDetailTab = switchDetailTab;
  window.addMember = addMember;
  window.removeMember = removeMember;
  window.doRemoveMember = doRemoveMember;
  window.logout = async function () {
    try { await api("/api/admin/auth/logout", { method: "POST" }); } catch (_) { /* session may be gone */ }
    redirectToLogin();
  };

  boot();
})();
