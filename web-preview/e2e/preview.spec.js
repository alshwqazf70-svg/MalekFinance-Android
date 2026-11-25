import { test, expect } from "@playwright/test";

const storage = "mk-finance-design-sandbox-v1";
test.beforeEach(async ({ page }) => {
  await page.goto("/");
});

test("opens an explicitly labeled preview with no runtime errors or overflow", async ({
  page,
}) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await expect(
    page.getByRole("heading", { name: "مساحتك المالية." }),
  ).toBeVisible();
  await expect(
    page.getByText("معاينة تفاعلية، وليست نسخة Android."),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect(errors).toEqual([]);
});

test("manual record is confirmed, searchable and persisted across reload", async ({
  page,
}) => {
  await page.getByRole("button", { name: "إضافة عملية", exact: true }).click();
  await page.locator("#modal [name=amount]").fill("١٥٠٠");
  await page.locator("#modal [name=note]").fill("اختبار دورة المعاينة");
  expect(
    await page.evaluate((key) => localStorage.getItem(key), storage),
  ).toBeNull();
  await page
    .getByRole("button", { name: "تأكيد وحفظ العملية التجريبية" })
    .click();
  await expect(page.locator("#modal")).not.toBeVisible();
  await page
    .getByRole("button", { name: "العمليات", exact: true })
    .first()
    .click();
  await page
    .getByRole("textbox", { name: "البحث في العمليات" })
    .fill("اختبار دورة المعاينة");
  await expect(
    page.locator("#transaction-results .transaction-row"),
  ).toHaveCount(1);
  await page.reload();
  expect(
    await page.evaluate(
      (key) =>
        JSON.parse(localStorage.getItem(key)).transactions.find(
          (r) => r.note === "اختبار دورة المعاينة",
        ).amount,
      storage,
    ),
  ).toBe("150000");
});

test("mobile navigation, all five pages and handset mode remain usable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  for (const name of ["العمليات", "المساعد", "التقارير", "مالي", "الرئيسية"]) {
    await page
      .locator(".bottom-nav")
      .getByRole("button", { name, exact: true })
      .click();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }
  const nav = await page.locator(".bottom-nav").boundingBox();
  expect(nav.y + nav.height).toBeLessThanOrEqual(845);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole("button", { name: "عرض بحجم الهاتف" }).click();
  await expect(page.locator(".app-shell")).toHaveClass(/phone-mode/);
  expect(
    (await page.locator(".app-shell").boundingBox()).width,
  ).toBeLessThanOrEqual(441);
});

test("privacy survives navigation and removes chart data from the DOM", async ({
  page,
}) => {
  await page.getByRole("button", { name: "إخفاء المبالغ" }).click();
  await expect(page.locator(".balance-value")).toHaveText("••••••");
  await expect(page.locator(".bar-chart")).toHaveCount(0);
  await page
    .getByRole("button", { name: "التقارير", exact: true })
    .first()
    .click();
  await page
    .getByRole("button", { name: "الرئيسية", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("button", { name: "إظهار المبالغ" }),
  ).toBeVisible();
});

test("text assistant uses a draft and never requests the microphone", async ({
  page,
}) => {
  let requestedMicrophone = false;
  await page.exposeFunction("microphoneRequested", () => {
    requestedMicrophone = true;
  });
  await page.evaluate(() => {
    if (navigator.mediaDevices)
      navigator.mediaDevices.getUserMedia = () => {
        window.microphoneRequested();
        return Promise.reject(new Error("not allowed in preview"));
      };
  });
  await page
    .getByRole("button", { name: "المساعد", exact: true })
    .first()
    .click();
  await expect(
    page.getByText("المحرك الصوتي لا يعمل في معاينة المتصفح هذه.", {
      exact: true,
    }),
  ).toBeVisible();
  await page.locator("#command").fill("صرفت ١٥٠٠ ريال بنزين");
  await page.getByRole("button", { name: "فهم العبارة ومراجعتها" }).click();
  await expect(
    page.getByRole("heading", { name: "راجع مسودتك قبل الحفظ" }),
  ).toBeVisible();
  await expect(page.locator("#modal [name=amount]")).toHaveValue("1500.00");
  await page.getByRole("button", { name: "إلغاء", exact: true }).click();
  expect(
    await page.evaluate((key) => localStorage.getItem(key), storage),
  ).toBeNull();
  expect(requestedMicrophone).toBe(false);
});

test("wallet creation and internal transfer update preview only; insufficient funds fail", async ({
  page,
}) => {
  await page.getByRole("button", { name: "مالي", exact: true }).first().click();
  await page.getByRole("button", { name: "محفظة جديدة", exact: true }).click();
  await page.locator("#modal [name=name]").fill("محفظة اختبار");
  await page.locator("#modal [name=opening]").fill("100");
  await page.getByRole("button", { name: "إنشاء محفظة تجريبية" }).click();
  await expect(
    page.getByRole("heading", { name: "محفظة اختبار" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "التحويلات", exact: true }).click();
  await page.getByRole("button", { name: "إنشاء تحويل تجريبي" }).click();
  await page.locator("#modal [name=amount]").fill("999999999");
  await page.getByRole("button", { name: "تأكيد التحويل التجريبي" }).click();
  await expect(page.locator("#modal .form-error")).toHaveText(
    "رصيد المحفظة المصدر غير كافٍ.",
  );
  await page.locator("#modal [name=amount]").fill("10");
  await page.getByRole("button", { name: "تأكيد التحويل التجريبي" }).click();
  expect(
    await page.evaluate(
      (key) => JSON.parse(localStorage.getItem(key)).transfers.length,
      storage,
    ),
  ).toBe(1);
});

test("untrusted transaction text is rendered as text, not markup", async ({
  page,
}) => {
  await page.getByRole("button", { name: "إضافة عملية", exact: true }).click();
  await page.locator("#modal [name=amount]").fill("1");
  await page.locator("#modal [name=note]").fill("<img src=x onerror=alert(1)>");
  await page
    .getByRole("button", { name: "تأكيد وحفظ العملية التجريبية" })
    .click();
  await expect(
    page.getByText("<img src=x onerror=alert(1)>", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".transaction-row img")).toHaveCount(0);
});

test("code is readable over HTTP without a download or sandbox file link", async ({ page, request }) => {
  await page.getByRole("link", { name: "فتح الكود وملفات التنزيل" }).click();
  await expect(page.getByRole("heading", { name: "المعاينة والكود في مكان واحد" })).toBeVisible();
  const code = await request.get("/preview-files/interface.txt");
  expect(code.status()).toBe(200);
  expect(code.headers()["content-type"]).toContain("text/plain");
  expect(await code.text()).toContain("function render()");
  const css = await request.get("/preview-files/design.txt");
  expect(await css.text()).toContain("--gold");
  // Downloads need an export, while the code viewer works on a fresh checkout.
  const archive = await request.head("/preview-files/source.zip");
  if (archive.status() === 200) {
    const pending = page.waitForEvent("download");
    await page.getByRole("link", { name: "تنزيل المعاينة مع كود الويب ZIP" }).click();
    const download = await pending;
    expect(download.suggestedFilename()).toBe("MK-Finance-preview-and-source.zip");
    expect(await download.failure()).toBeNull();
  } else {
    expect(archive.status()).toBe(404);
  }
});

test("credit purchase records dates without cash movement and produces a clear statement after repayment", async ({ page }) => {
  await page.getByRole("button", { name: "مالي", exact: true }).first().click();
  await page.getByRole("button", { name: "دفتر الدين", exact: true }).click();
  await page.getByRole("button", { name: "شراء بالدين", exact: true }).click();
  await page.locator('#modal [name=person]').fill('عبده مهدي');
  await page.locator('#modal [name=amount]').fill('1000');
  await page.locator('#modal [name=at]').fill('2026-11-24T10:20:30');
  await page.locator('#modal [name=ack]').check();
  await page.getByRole('button', { name: 'تأكيد تسجيل الشراء بالدين', exact: true }).click();
  const transactionCount = await page.evaluate(key => JSON.parse(localStorage.getItem(key)).transactions.length, storage);
  await page.getByRole("button", { name: "شراء بالدين", exact: true }).click();
  await page.locator('#modal [name=person]').fill('عبده مهدي');
  await page.locator('#modal [name=amount]').fill('2000');
  await page.locator('#modal [name=at]').fill('2026-11-25T10:20:30');
  await page.locator('#modal [name=ack]').check();
  await page.getByRole('button', { name: 'تأكيد تسجيل الشراء بالدين', exact: true }).click();
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key)).transactions.length, storage)).toBe(transactionCount);
  await page.getByRole('button', { name: 'تسجيل سداد الحساب', exact: true }).click();
  await page.locator('#modal [name=amount]').fill('1500');
  await page.getByRole('button', { name: 'تأكيد تسجيل سداد الحساب', exact: true }).click();
  await page.getByRole('button', { name: 'معاينة ومشاركة كشف الحساب', exact: true }).click();
  await expect(page.locator('.statement-paper')).toContainText('عبده مهدي');
  await expect(page.locator('.statement-paper')).toContainText('3000.00 YER');
  await expect(page.locator('.statement-paper')).toContainText('1500.00 YER');
  await expect(page.locator('.statement-paper')).toContainText('Asia/Aden');
  await expect(page.locator('.statement-paper')).toContainText('سداد حساب');
  await expect(page.locator('#share-credit')).toBeVisible();
  await page.getByRole('button', { name: 'إغلاق', exact: true }).click();
  await page.reload();
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key)).creditPurchases.length, storage)).toBe(2);
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key)).creditPayments.length, storage)).toBe(1);
});

test('short qat phrase opens an unconfirmed full-name purchase draft without recording anything', async ({ page }) => {
  await page.getByRole('button', { name: 'المساعد', exact: true }).first().click();
  await page.locator('#command').fill('ألف حق قات عبده مهدي');
  await page.getByRole('button', { name: 'فهم العبارة ومراجعتها' }).click();
  await expect(page.getByRole('heading', { name: 'مراجعة شراء بالدين' })).toBeVisible();
  await expect(page.locator('#modal [name=person]')).toHaveValue('عبده مهدي');
  await expect(page.locator('#modal [name=amount]')).toHaveValue('1000.00');
  await expect(page.locator('#modal [name=ack]')).not.toBeChecked();
  expect(await page.evaluate(key => localStorage.getItem(key), storage)).toBeNull();
});

test('credit ledger stays within mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'مالي', exact: true }).last().click();
  await page.getByRole('button', { name: 'دفتر الدين', exact: true }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.getByRole('button', { name: 'شراء بالدين', exact: true }).click();
  expect(await page.evaluate(() => document.querySelector('#modal').scrollWidth <= innerWidth)).toBe(true);
});

test('unreadable stored data is preserved until the user explicitly resets the preview', async ({ page }) => {
  const unreadable = '{"broken ledger":';
  await page.evaluate(({ key, value }) => localStorage.setItem(key, value), { key: storage, value: unreadable });
  await page.reload();
  await expect(page.getByText('وجدنا بيانات سابقة غير مقروءة؛ لم نحذفها ولن نستبدلها دون تأكيد إعادة التجربة.')).toBeVisible();
  await page.getByRole('button', { name: 'إضافة عملية', exact: true }).click();
  await page.locator('#modal [name=amount]').fill('100');
  await page.locator('#modal [name=note]').fill('تغيير مؤقت دون استبدال النسخة السابقة');
  await page.getByRole('button', { name: 'تأكيد وحفظ العملية التجريبية' }).click();
  expect(await page.evaluate(key => localStorage.getItem(key), storage)).toBe(unreadable);
  await page.getByRole('button', { name: 'إعادة التجربة', exact: true }).click();
  expect(await page.evaluate(key => localStorage.getItem(key), storage)).toBe(unreadable);
  await page.getByRole('button', { name: 'نعم، أعد الأمثلة' }).click();
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key)).version, storage)).toBe(1);
});
