import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// Durante el desarrollo, las llamadas a /api se redirigen al backend (Spring Boot).
// El destino se puede cambiar con VITE_PROXY_TARGET (útil para pruebas con un servidor simulado).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: env.VITE_PROXY_TARGET || "http://localhost:8085",
          changeOrigin: true,
        },
      },
    },
  };
});
