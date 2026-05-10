import rateLimit from "express-rate-limit";

const STANDARD_OPTS = {
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { code: "RATE_LIMITED", message: "Слишком много запросов. Попробуйте позже." }
};

export const signInLimiter = rateLimit({
  ...STANDARD_OPTS,
  windowMs: 15 * 60 * 1000,
  limit: 10
});

export const signUpLimiter = rateLimit({
  ...STANDARD_OPTS,
  windowMs: 60 * 60 * 1000,
  limit: 5
});

export const passwordResetLimiter = rateLimit({
  ...STANDARD_OPTS,
  windowMs: 60 * 60 * 1000,
  limit: 5
});

export const verifyEmailLimiter = rateLimit({
  ...STANDARD_OPTS,
  windowMs: 60 * 60 * 1000,
  limit: 10
});
