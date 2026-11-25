"""Package only explicit preview sources and the standalone HTML, never local data."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent.parent
output = root / "exports" / "MK-Finance-preview-and-source.zip"
with ZipFile(output, "w", ZIP_DEFLATED) as archive:
    archive.write(root / "exports" / "MK-Finance-preview.html", "MK-Finance-preview.html")
    for folder in ["src", "public", "scripts", "tests", "e2e"]:
        for path in sorted((root / folder).rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                archive.write(path, "source/" + path.relative_to(root).as_posix())
    for name in ["package.json", "package-lock.json", "vite.config.js", "playwright.config.js", "index.html", ".gitignore"]:
        archive.write(root / name, "source/" + name)
    archive.writestr("README.txt", "MK Finance — معاينة تصميم وليست Android\n\nافتح MK-Finance-preview.html بمتصفح يدعم JavaScript في ملفات HTML المحلية.\nكود الويب داخل source. للتطوير من داخله: npm ci ثم npm run dev.\nلإنشاء حزمة جديدة: npm run export:bundle (يتطلب Node.js وPython 3).\nالبيانات تجريبية والصوت الأصلي غير مفعّل هنا.\n")
print(f"Preview and source ZIP: {output} ({output.stat().st_size} bytes)")
