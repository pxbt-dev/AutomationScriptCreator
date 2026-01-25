const { chromium } = require('playwright');

class PlaywrightManager {
    constructor() {
        this.sessions = new Map();
        this.browser = null;
    }

    async initialize() {
        if (!this.browser) {
            this.browser = await chromium.launch({
                headless: true,
                args: ['--no-sandbox', '--disable-dev-shm-usage']
            });
        }
    }

    async createSession(sessionId, url, headless = true, viewport = { width: 1920, height: 1080 }) {
        await this.initialize();

        const context = await this.browser.newContext({ viewport });
        const page = await context.newPage();

        await page.goto(url, { waitUntil: 'networkidle' });

        this.sessions.set(sessionId, { context, page });

        return sessionId;
    }

    async captureScreenshot(sessionId, selector = null, fullPage = false) {
        const session = this.sessions.get(sessionId);
        if (!session) {
            throw new Error(`Session ${sessionId} not found`);
        }

        const { page } = session;
        const timestamp = Date.now();
        const filename = `screenshot_${sessionId}_${timestamp}.png`;

        let screenshot;
        if (selector) {
            const element = await page.$(selector);
            if (!element) {
                throw new Error(`Element with selector "${selector}" not found`);
            }
            screenshot = await element.screenshot({ path: `./screenshots/${filename}` });
        } else {
            screenshot = await page.screenshot({
                path: `./screenshots/${filename}`,
                fullPage
            });
        }

        return filename;
    }

    async getElementInfo(sessionId, selector) {
        const session = this.sessions.get(sessionId);
        if (!session) {
            throw new Error(`Session ${sessionId} not found`);
        }

        const { page } = session;
        const element = await page.$(selector);

        if (!element) {
            throw new Error(`Element with selector "${selector}" not found`);
        }

        const info = await element.evaluate((el) => {
            const attributes = {};
            for (const attr of el.attributes) {
                attributes[attr.name] = attr.value;
            }

            const bounds = el.getBoundingClientRect();

            return {
                tagName: el.tagName.toLowerCase(),
                text: el.textContent?.trim() || '',
                attributes,
                bounds: {
                    x: bounds.x,
                    y: bounds.y,
                    width: bounds.width,
                    height: bounds.height
                },
                visible: el.offsetParent !== null,
                computedStyle: window.getComputedStyle(el)
            };
        });

        return info;
    }

    async closeSession(sessionId) {
        const session = this.sessions.get(sessionId);
        if (session) {
            await session.page.close();
            await session.context.close();
            this.sessions.delete(sessionId);
        }
    }

    async shutdown() {
        for (const [sessionId] of this.sessions) {
            await this.closeSession(sessionId);
        }

        if (this.browser) {
            await this.browser.close();
        }
    }
}

module.exports = { PlaywrightManager };