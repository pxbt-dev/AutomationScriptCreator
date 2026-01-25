const { Server } = require('@modelcontextprotocol/sdk/server/index.js');
const { StdioServerTransport } = require('@modelcontextprotocol/sdk/server/stdio.js');
const { PlaywrightManager } = require('/PlaywrightManager.js');
const { z } = require('zod');

class PlaywrightMCPServer {
    constructor() {
        this.server = new Server(
            {
                name: 'playwright-mcp-server',
                version: '1.0.0',
            },
            {
                capabilities: {
                    resources: {},
                    tools: {
                        create_session: {
                            description: 'Create a new Playwright browser session',
                            inputSchema: {
                                type: 'object',
                                properties: {
                                    sessionId: { type: 'string' },
                                    url: { type: 'string' },
                                    headless: { type: 'boolean', default: true },
                                    viewport: {
                                        type: 'object',
                                        properties: {
                                            width: { type: 'number', default: 1920 },
                                            height: { type: 'number', default: 1080 }
                                        }
                                    }
                                },
                                required: ['sessionId', 'url']
                            }
                        },
                        capture_screenshot: {
                            description: 'Capture screenshot of page or element',
                            inputSchema: {
                                type: 'object',
                                properties: {
                                    sessionId: { type: 'string' },
                                    selector: { type: 'string' },
                                    fullPage: { type: 'boolean', default: false }
                                },
                                required: ['sessionId']
                            }
                        },
                        get_element_info: {
                            description: 'Get detailed information about an element',
                            inputSchema: {
                                type: 'object',
                                properties: {
                                    sessionId: { type: 'string' },
                                    selector: { type: 'string' }
                                },
                                required: ['sessionId', 'selector']
                            }
                        },
                        close_session: {
                            description: 'Close a browser session',
                            inputSchema: {
                                type: 'object',
                                properties: {
                                    sessionId: { type: 'string' }
                                },
                                required: ['sessionId']
                            }
                        }
                    }
                }
            }
        );

        this.playwrightManager = new PlaywrightManager();

        this.setupToolHandlers();
    }

    setupToolHandlers() {
        // Create session
        this.server.setRequestHandler('tools/create_session', async (request) => {
            const { sessionId, url, headless = true, viewport = { width: 1920, height: 1080 } } = request.params;

            try {
                await this.playwrightManager.createSession(sessionId, url, headless, viewport);
                return {
                    content: [{
                        type: 'text',
                        text: `Session ${sessionId} created successfully`
                    }]
                };
            } catch (error) {
                throw new Error(`Failed to create session: ${error.message}`);
            }
        });

        // Capture screenshot
        this.server.setRequestHandler('tools/capture_screenshot', async (request) => {
            const { sessionId, selector, fullPage = false } = request.params;

            try {
                const filename = await this.playwrightManager.captureScreenshot(sessionId, selector, fullPage);
                return {
                    content: [{
                        type: 'text',
                        text: `Screenshot captured: ${filename}`,
                        data: { filename }
                    }]
                };
            } catch (error) {
                throw new Error(`Failed to capture screenshot: ${error.message}`);
            }
        });

        // Get element info
        this.server.setRequestHandler('tools/get_element_info', async (request) => {
            const { sessionId, selector } = request.params;

            try {
                const elementInfo = await this.playwrightManager.getElementInfo(sessionId, selector);
                return {
                    content: [{
                        type: 'text',
                        text: 'Element information retrieved',
                        data: elementInfo
                    }]
                };
            } catch (error) {
                throw new Error(`Failed to get element info: ${error.message}`);
            }
        });

        // Close session
        this.server.setRequestHandler('tools/close_session', async (request) => {
            const { sessionId } = request.params;

            try {
                await this.playwrightManager.closeSession(sessionId);
                return {
                    content: [{
                        type: 'text',
                        text: `Session ${sessionId} closed`
                    }]
                };
            } catch (error) {
                throw new Error(`Failed to close session: ${error.message}`);
            }
        });
    }

    async run() {
        const transport = new StdioServerTransport();
        await this.server.connect(transport);
        console.error('Playwright MCP server running on stdio');
    }
}

// Start server
if (require.main === module) {
    const server = new PlaywrightMCPServer();
    server.run().catch(console.error);
}

module.exports = { PlaywrightMCPServer };