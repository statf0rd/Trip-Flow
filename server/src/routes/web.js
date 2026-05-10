import express from "express";
import { consumeEmailVerification, lookupPasswordReset, consumePasswordReset } from "../tokens.js";
import { createPasswordHash } from "../auth.js";

const PAGE_STYLE = `
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background:#F8FAFC; color:#0F172A; margin:0; padding:32px; }
  .card { max-width: 420px; margin: 32px auto; padding: 24px 28px; background:#fff; border-radius:16px; box-shadow:0 4px 20px rgba(15,23,42,.08); }
  h1 { font-size:20px; margin:0 0 12px; }
  p { line-height:1.5; }
  input { width:100%; padding:10px 12px; font-size:15px; border:1px solid #cbd5e1; border-radius:8px; box-sizing:border-box; }
  button { width:100%; padding:12px; font-size:15px; font-weight:600; background:#FF6B5B; color:#fff; border:0; border-radius:8px; cursor:pointer; margin-top:12px; }
  .muted { color:#64748b; font-size:13px; }
  .error { color:#b91c1c; }
  .ok { color:#15803d; }
</style>`;

function pageHtml({ title, body }) {
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>${PAGE_STYLE}</head><body><div class="card">${body}</div></body></html>`;
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

export function webRouter(db) {
  const router = express.Router();

  router.get("/verify-email", (req, res) => {
    const token = String(req.query?.token || "").trim();
    if (!token) {
      res.status(400).type("html").send(pageHtml({
        title: "Triloo — подтверждение email",
        body: `<h1>Ссылка недействительна</h1><p>Не указан токен.</p>`
      }));
      return;
    }
    const result = consumeEmailVerification(db, token);
    if (!result.ok) {
      const reason =
        result.reason === "EXPIRED" ? "Срок действия ссылки истёк." :
        result.reason === "ALREADY_USED" ? "Ссылка уже использовалась." :
        "Ссылка недействительна.";
      res.status(400).type("html").send(pageHtml({
        title: "Triloo — подтверждение email",
        body: `<h1>Не удалось подтвердить email</h1><p class="error">${reason}</p><p class="muted">Запросите новое письмо в приложении.</p>`
      }));
      return;
    }
    res.type("html").send(pageHtml({
      title: "Triloo — email подтверждён",
      body: `<h1>Email подтверждён ✅</h1><p>Готово. Можно вернуться в приложение Triloo.</p>`
    }));
  });

  router.get("/reset-password", (req, res) => {
    const token = String(req.query?.token || "").trim();
    if (!token) {
      res.status(400).type("html").send(pageHtml({
        title: "Triloo — сброс пароля",
        body: `<h1>Ссылка недействительна</h1><p>Не указан токен.</p>`
      }));
      return;
    }
    const lookup = lookupPasswordReset(db, token);
    if (!lookup.ok) {
      const reason =
        lookup.reason === "EXPIRED" ? "Срок действия ссылки истёк (1 час)." :
        lookup.reason === "ALREADY_USED" ? "Ссылка уже использовалась." :
        "Ссылка недействительна.";
      res.status(400).type("html").send(pageHtml({
        title: "Triloo — сброс пароля",
        body: `<h1>Не удалось сбросить пароль</h1><p class="error">${reason}</p>`
      }));
      return;
    }
    res.type("html").send(pageHtml({
      title: "Triloo — сброс пароля",
      body: `
        <h1>Новый пароль</h1>
        <p class="muted">Для аккаунта ${escapeHtml(lookup.email)}.</p>
        <form method="POST" action="/reset-password">
          <input type="hidden" name="token" value="${escapeHtml(token)}">
          <label>
            <span class="muted">Минимум 6 символов</span>
            <input type="password" name="password" minlength="6" required autocomplete="new-password" autofocus>
          </label>
          <button type="submit">Сохранить</button>
        </form>
      `
    }));
  });

  router.post("/reset-password", express.urlencoded({ extended: false }), (req, res) => {
    const token = String(req.body?.token || "").trim();
    const password = String(req.body?.password || "");
    if (!token || password.length < 6) {
      res.status(400).type("html").send(pageHtml({
        title: "Triloo — сброс пароля",
        body: `<h1>Ошибка</h1><p class="error">Пароль слишком короткий или токен пуст.</p>`
      }));
      return;
    }
    const lookup = lookupPasswordReset(db, token);
    if (!lookup.ok) {
      res.status(400).type("html").send(pageHtml({
        title: "Triloo — сброс пароля",
        body: `<h1>Ошибка</h1><p class="error">Ссылка недействительна или просрочена.</p>`
      }));
      return;
    }
    const credentials = createPasswordHash(password);
    db.prepare(
      `UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?`
    ).run(credentials.passwordHash, credentials.passwordSalt, lookup.userId);
    db.prepare(`DELETE FROM sessions WHERE user_id = ?`).run(lookup.userId);
    consumePasswordReset(db, token);
    res.type("html").send(pageHtml({
      title: "Triloo — пароль обновлён",
      body: `<h1 class="ok">Пароль обновлён ✅</h1><p>Войдите в приложение Triloo с новым паролем.</p>`
    }));
  });

  return router;
}
