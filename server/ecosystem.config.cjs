module.exports = {
  apps: [
    {
      name: "triloo-sync",
      cwd: "/root/rustore/triloo",
      script: "src/index.js",
      env: {
        PORT: 8091,
        DATA_DIR: "./data",
        ALLOWED_ORIGIN: "*"
      }
    }
  ]
};
