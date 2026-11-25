import { defineConfig } from "vite";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const read = (name) => readFile(fileURLToPath(new URL(name, import.meta.url)));
const files = {
  "/preview-files/preview.html": ["./exports/MK-Finance-preview.html", "text/html; charset=utf-8", "MK-Finance-preview.html"],
  "/preview-files/source.zip": ["./exports/MK-Finance-preview-and-source.zip", "application/zip", "MK-Finance-preview-and-source.zip"],
  "/preview-files/interface.txt": ["./src/main.js", "text/plain; charset=utf-8"],
  "/preview-files/design.txt": ["./src/style.css", "text/plain; charset=utf-8"],
  "/preview-files/credit.txt": ["./src/credit.js", "text/plain; charset=utf-8"],
  "/preview-files/calculations.txt": ["./src/finance.js", "text/plain; charset=utf-8"],
};

// Explicit, same-origin routes: no sandbox: links and no arbitrary file serving.
function previewFiles() {
  return {
    name: "mk-preview-files",
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const path = new URL(req.url || "/", "http://preview").pathname;
        if (path === "/preview-files/" || path === "/preview-files") {
          res.setHeader("Content-Type", "text/html; charset=utf-8");
          res.setHeader("Cache-Control", "no-store");
          res.end(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>MK Finance — ملفات المعاينة والكود</title><style>body{background:#080f13;color:#f4f1e9;font:18px/1.9 system-ui,sans-serif;max-width:720px;margin:40px auto;padding:20px}a{display:block;color:#e3c68d;background:#111d23;border:1px solid #2b3c43;border-radius:12px;padding:14px 18px;margin:14px 0;text-decoration:none}p{color:#a5b7bd}small{display:block;color:#a5b7bd}</style><h1>المعاينة والكود في مكان واحد</h1><p>هذه روابط ويب عادية داخل المعاينة، وليست روابط ملفات داخل المحادثة. يمكنك قراءة الكود دون تنزيل أي ملف.</p><a href="/">← الدخول إلى المعاينة التفاعلية</a><h2>اقرأ الكود مباشرة</h2><a href="/preview-files/interface.txt">كود واجهة المعاينة — JavaScript</a><a href="/preview-files/design.txt">كود التصميم والألوان — CSS</a><a href="/preview-files/calculations.txt">كود الحسابات التجريبية</a><a href="/preview-files/credit.txt">كود دفتر المشتريات بالدين والكشف</a><h2>ملفات التنزيل</h2><a href="/preview-files/preview.html" download>تنزيل المعاينة المستقلة HTML</a><a href="/preview-files/source.zip" download>تنزيل المعاينة مع كود الويب ZIP</a><small>المعاينة منفصلة عن تطبيق Android، ببيانات تجريبية ودون تشغيل محرك الصوت. هذه الصفحة تحتاج وصولاً سليماً إلى بيئة المعاينة؛ لا تتجاوز خطأ Sandbox not found إذا تعذّر الوصول إلى البيئة نفسها.</small></html>`);
          return;
        }
        const entry = files[path];
        if (!entry) { next(); return; }
        if (req.method !== "GET" && req.method !== "HEAD") {
          res.statusCode = 405;
          res.setHeader("Allow", "GET, HEAD");
          res.end();
          return;
        }
        try {
          const content = await read(entry[0]);
          res.setHeader("Content-Type", entry[1]);
          res.setHeader("X-Content-Type-Options", "nosniff");
          res.setHeader("Cache-Control", "no-store");
          res.setHeader("Content-Length", content.length);
          if (entry[2]) res.setHeader("Content-Disposition", `attachment; filename="${entry[2]}"`);
          res.end(req.method === "HEAD" ? undefined : content);
        } catch {
          res.statusCode = 404;
          res.setHeader("Content-Type", "text/plain; charset=utf-8");
          res.end("ملف التنزيل لم يُجهّز بعد. ما زالت المعاينة والكود النصي متاحين؛ يلزم إعادة توليد حزمة التصدير.");
        }
      });
    },
  };
}

export default defineConfig({
  plugins: [previewFiles()],
  server: {
    host: "0.0.0.0",
    port: 5174,
    strictPort: true,
    allowedHosts: [".e2b.app"],
  },
});
