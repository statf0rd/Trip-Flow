module.exports = {
  apps: [
    {
      name: "triloo-backend",
      cwd: "/root/triloo",
      script: "src/index.js",
      // Чувствительные SMTP-параметры читаются из /root/triloo/.env (chmod 600)
      // через нативный Node флаг --env-file (без зависимости от dotenv).
      node_args: "--env-file=/root/triloo/.env",
      env: {
        NODE_ENV: "production",
        PORT: 8091,
        HOST: "127.0.0.1",
        DATA_DIR: "/root/triloo/data",
        ALLOWED_ORIGIN: "*",
        PUBLIC_BASE_URL: "https://triloo.85.192.61.86.nip.io",
        TRUST_PROXY: "1",
        // Включить полный verify-flow при sign-up (письмо + email_verified=0 до клика).
        // Сейчас "false" — регистрация без верификации, новый юзер сразу email_verified=1.
        REQUIRE_EMAIL_VERIFICATION: "false"
      },
      max_memory_restart: "256M",
      error_file: "/root/triloo/logs/error.log",
      out_file: "/root/triloo/logs/out.log",
      time: true
    }
  ]
};
