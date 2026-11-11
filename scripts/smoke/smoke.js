#!/usr/bin/env node
/*
 * 배포 후 화면 점검 (ROADMAP E7) — 사람이 휴대폰 화면으로 돌아보던 점검을 그대로 옮긴 것.
 *
 *   node scripts/smoke/smoke.js https://52-79-238-6.sslip.io
 *
 * 390px(휴대폰)·1280px(PC)로 주요 화면을 열어 다음을 확인한다. 하나라도 걸리면 종료 코드 1.
 *   - 같은 사이트 요청의 4xx/5xx, 페이지 스크립트 오류
 *   - 가로 넘침(문서가 화면보다 넓다)
 *   - 화면 밖으로 잘린 버튼(가로 스크롤 영역 안의 것은 제외) — 2026-11 조건 찾기 제출 버튼이 이렇게 잘렸다(c4ca005)
 *   - 조건 찾기 마지막 단계까지 입력해 "신청 가능한 공고 찾기"가 화면 안에 있는지, 결과가 나오는지
 *
 * Playwright 가 필요하다(npm i -g playwright 또는 NODE_PATH 로 위치 지정). 판정·데이터는 건드리지 않는다
 * — GET 과 조건 찾기 POST 한 번뿐이고, 관리자 API 는 부르지 않는다.
 */
const { chromium } = require('playwright');

const base = (process.argv[2] || process.env.SMOKE_BASE_URL || '').replace(/\/$/, '');
if (!base) {
    console.error('사용법: node scripts/smoke/smoke.js <사이트 주소>');
    process.exit(2);
}
const origin = new URL(base).origin;
const problems = [];
const note = (where, what) => problems.push(`${where}: ${what}`);

async function inspect(page, label) {
    const r = await page.evaluate(() => {
        const vw = document.documentElement.clientWidth;
        const overflow = document.documentElement.scrollWidth - vw;
        const inScroller = el => {
            for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
                const ox = getComputedStyle(p).overflowX;
                if (ox === 'auto' || ox === 'scroll' || ox === 'hidden') return true;
            }
            return false;
        };
        const clipped = [...document.querySelectorAll('.btn, button, [role=button]')]
            .filter(el => el.offsetParent !== null && !inScroller(el))
            .map(el => ({ el, box: el.getBoundingClientRect() }))
            .filter(({ box }) => box.width > 0 && (box.right > vw + 1 || box.left < -1))
            .map(({ el, box }) => `"${(el.innerText || el.getAttribute('aria-label') || '').trim().slice(0, 30)}" (${Math.round(box.left)}~${Math.round(box.right)}px / 화면 ${vw}px)`);
        return { overflow, clipped };
    });
    if (r.overflow > 2) note(label, `가로 넘침 ${r.overflow}px`);
    r.clipped.forEach(c => note(label, `잘린 버튼 ${c}`));
}

async function run(viewport, name) {
    const browser = await chromium.launch();
    const ctx = await browser.newContext({ viewport, locale: 'ko-KR' });
    const page = await ctx.newPage();
    let current = '';
    page.on('pageerror', e => note(`${name} ${current}`, `스크립트 오류 ${e.message.slice(0, 120)}`));
    page.on('response', res => {
        if (res.status() >= 400 && res.url().startsWith(origin)) note(`${name} ${current}`, `HTTP ${res.status()} ${res.url()}`);
    });
    const visit = async (path, label) => {
        current = label;
        const res = await page.goto(base + path, { waitUntil: 'networkidle' });
        if (!res || res.status() >= 400) { note(`${name} ${label}`, `열리지 않음 (${res && res.status()})`); return false; }
        await inspect(page, `${name} ${label}`);
        return true;
    };

    // 목록에서 판정 대상 공고 하나를 고른다(접수중·접수예정, "자격 판정 안 함"이 아닌 것)
    if (!(await visit('/announcements', '공고 목록'))) { await browser.close(); return; }
    const target = await page.evaluate(() => {
        const card = [...document.querySelectorAll('.notice-card[data-ann-id]')]
            .find(c => !c.innerText.includes('자격 판정 안 함'));
        return card ? card.dataset.annId : null;
    });
    const navPaths = await page.evaluate(() => [...new Set([...document.querySelectorAll('.site-header nav a, .tabbar a')]
        .map(a => new URL(a.href).pathname))]);
    for (const p of navPaths) if (p !== '/announcements') await visit(p, p);
    if (target) {
        await visit(`/announcements/${target}`, `공고 상세 #${target}`);
        await visit(`/announcements/${target}/eligibility`, `판정 폼 #${target}`);
    } else {
        note(name, '판정 대상 공고를 목록에서 찾지 못함');
    }
    await visit('/quick?married=true&houseless=true&childCount=1', '30초 진단 결과');

    // 조건 찾기 — 단계별 입력 후 마지막 단계에서 제출 버튼 위치, 제출 후 결과
    if (await visit('/match', '조건 찾기')) {
        const fill = async (field, value) => {
            const el = page.locator(`[name="${field}"]:not([type=hidden])`).first();
            if (!(await el.count()) || !(await el.isVisible())) return;
            const type = await el.getAttribute('type');
            if (type === 'checkbox') await el.check();
            else if ((await el.evaluate(e => e.tagName)) === 'SELECT') await el.selectOption(String(value));
            else await el.fill(String(value));
        };
        const steps = [{ married: 1, monthsSinceMarriage: 30, houseless: 1 },
                       { accountMonths: 36, accountPaymentCount: 36, householdSize: 3 },
                       { monthlyHouseholdIncome: 5000000 }];
        for (const step of steps) {
            for (const [k, v] of Object.entries(step)) await fill(k, v);
            const next = page.locator('#matchNext');
            if (await next.isVisible()) { await next.click(); await page.waitForTimeout(200); }
        }
        current = '조건 찾기 마지막 단계';
        await inspect(page, `${name} ${current}`);
        const submit = page.locator('#matchSubmit');
        const box = await submit.boundingBox();
        const vw = viewport.width;
        if (!box) note(`${name} ${current}`, '제출 버튼이 보이지 않음');
        else if (box.x + box.width > vw + 1) note(`${name} ${current}`, `제출 버튼이 화면 밖 (${Math.round(box.x + box.width)}px / ${vw}px)`);
        else {
            await submit.click();
            await page.waitForLoadState('networkidle');
            current = '조건 찾기 결과';
            const text = await page.locator('main').innerText();
            if (!/신청 가능한 공고|조건에 맞는 공고/.test(text)) note(`${name} ${current}`, '결과 화면 문구가 없음');
            await inspect(page, `${name} ${current}`);
        }
    }
    await browser.close();
}

(async () => {
    await run({ width: 390, height: 844 }, '[390px]');
    await run({ width: 1280, height: 800 }, '[1280px]');
    if (problems.length) {
        console.log(`화면 점검 실패 ${problems.length}건 — ${base}`);
        problems.forEach(p => console.log(' - ' + p));
        process.exit(1);
    }
    console.log(`화면 점검 통과 — ${base}`);
})().catch(e => { console.error(e); process.exit(1); });
