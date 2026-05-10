import nodemailer from "nodemailer";

const SMTP_HOST = process.env.SMTP_HOST || "";
const SMTP_PORT = Number(process.env.SMTP_PORT || 587);
const SMTP_USER = process.env.SMTP_USER || "";
const SMTP_PASS = process.env.SMTP_PASS || "";
const SMTP_SECURE = String(process.env.SMTP_SECURE || "").toLowerCase() === "true";
const MAIL_FROM = process.env.MAIL_FROM || "Triloo <no-reply@triloo.local>";

let transporter = null;
let mailerReady = false;

if (SMTP_HOST) {
  transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port: SMTP_PORT,
    secure: SMTP_SECURE,
    auth: SMTP_USER ? { user: SMTP_USER, pass: SMTP_PASS } : undefined
  });
  mailerReady = true;
  console.log(`[mailer] configured: ${SMTP_HOST}:${SMTP_PORT} secure=${SMTP_SECURE}`);
} else {
  console.warn("[mailer] SMTP_HOST not set — emails will be logged to stdout only");
}

export function isMailerConfigured() {
  return mailerReady;
}

export async function sendMail({ to, subject, text, html }) {
  if (!mailerReady) {
    console.log(`[mailer:dryrun] to=${to} subject=${subject}\n--- text ---\n${text}\n--- html ---\n${html}\n`);
    return { dryRun: true };
  }
  const info = await transporter.sendMail({ from: MAIL_FROM, to, subject, text, html });
  console.log(`[mailer] sent to=${to} subject="${subject}" messageId=${info.messageId}`);
  return info;
}

export function buildVerificationEmail({ displayName, link }) {
  const subject = "Triloo: подтвердите email";
  const text = [
    `Здравствуйте${displayName ? ", " + displayName : ""}!`,
    "",
    "Подтвердите email, чтобы активировать аккаунт Triloo:",
    link,
    "",
    "Ссылка действительна 24 часа. Если вы не регистрировались — просто проигнорируйте это письмо."
  ].join("\n");
  const html = `
    <p>Здравствуйте${displayName ? ", " + escapeHtml(displayName) : ""}!</p>
    <p>Подтвердите email, чтобы активировать аккаунт Triloo:</p>
    <p><a href="${link}">${link}</a></p>
    <p style="color:#666">Ссылка действительна 24 часа. Если вы не регистрировались — проигнорируйте письмо.</p>
  `.trim();
  return { subject, text, html };
}

export function buildPasswordResetEmail({ displayName, link }) {
  const subject = "Triloo: сброс пароля";
  const text = [
    `Здравствуйте${displayName ? ", " + displayName : ""}!`,
    "",
    "Перейдите по ссылке, чтобы задать новый пароль:",
    link,
    "",
    "Ссылка действительна 1 час. Если вы не запрашивали сброс — проигнорируйте это письмо."
  ].join("\n");
  const html = `
    <p>Здравствуйте${displayName ? ", " + escapeHtml(displayName) : ""}!</p>
    <p>Перейдите по ссылке, чтобы задать новый пароль:</p>
    <p><a href="${link}">${link}</a></p>
    <p style="color:#666">Ссылка действительна 1 час. Если вы не запрашивали сброс — проигнорируйте письмо.</p>
  `.trim();
  return { subject, text, html };
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
