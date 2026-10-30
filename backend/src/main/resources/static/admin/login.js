(() => {
  "use strict";

  const form = document.getElementById("login-form");
  const username = document.getElementById("login-username");
  const password = document.getElementById("login-password");
  const errorBox = document.getElementById("login-error");
  const submit = document.getElementById("login-submit");

  function showError(message) {
    errorBox.hidden = !message;
    errorBox.textContent = message || "";
  }

  async function checkSession() {
    try {
      const response = await fetch("/api/admin/auth/session", { credentials: "same-origin" });
      if (response.ok) {
        window.location.replace("/admin/");
        return;
      }
    } catch (_) {
      // Keep the form available when the network is temporarily unavailable.
    }
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    showError("");
    submit.disabled = true;
    submit.textContent = "验证中…";
    try {
      const response = await fetch("/api/admin/auth/login", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username.value.trim(), password: password.value }),
      });
      let payload = null;
      try { payload = await response.json(); } catch (_) { /* no body */ }
      if (!response.ok) {
        throw new Error(payload?.message || `登录失败 (${response.status})`);
      }
      window.location.replace("/admin/");
    } catch (error) {
      showError(error.message || "登录失败，请稍后重试");
      password.value = "";
      password.focus();
    } finally {
      submit.disabled = false;
      submit.textContent = "进入管理后台";
    }
  });

  checkSession();
})();
