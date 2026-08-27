import { createRoot } from "react-dom/client";
import App from "./app/App.tsx";
import "./styles/index.css";

function showBootError(message: string) {
  const boot = document.getElementById("boot");
  if (boot) {
    boot.textContent = `Erreur UI BDXv2 : ${message}`;
    return;
  }
  document.body.textContent = `Erreur UI BDXv2 : ${message}`;
}

window.addEventListener("error", (ev) => {
  const err = ev.error instanceof Error ? ev.error.stack ?? ev.error.message : ev.message;
  showBootError(err);
});
window.addEventListener("unhandledrejection", (ev) => {
  showBootError(String(ev.reason));
});

const root = document.getElementById("root");

try {
  if (!root) {
    throw new Error("Élément #root introuvable");
  }
  createRoot(root).render(<App />);
} catch (err) {
  showBootError(err instanceof Error ? err.stack ?? err.message : String(err));
}
