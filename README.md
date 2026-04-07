# Automation Script Creator

A Spring Boot web app that generates and runs Playwright test scripts from a website URL or a Swagger/OpenAPI spec.

## Requirements

- Java 21
- Maven
- Node.js (for running generated Playwright tests)
- [Ollama](https://ollama.ai) running locally — optional, used for AI-enhanced test generation

## Running

```bash
mvn spring-boot:run
```

App starts on [http://localhost:8080](http://localhost:8080).

Or here for a demo https://automationscriptcreator-production.up.railway.app/

## Usage

### Website testing
1. Enter a URL and click **Analyse** — the app crawls the page and discovers elements
2. Click **Generate Tests** — produces test cases and automatically generates a Playwright `.spec.js` script
3. The script is shown in the UI with options to copy or download it
4. Click **▶ Run** to execute the script via `npx playwright test` on the server
5. Results and a link to the HTML report appear inline

### API testing (Swagger / OpenAPI)
1. Enter a Swagger 2.0 or OpenAPI 3.0 spec URL and click **Parse Spec**
2. Click **Generate Tests** — produces test cases for each endpoint
3. Click **Run All Tests** to execute the API tests against the live endpoints
4. Pass rate and per-endpoint results are shown inline

Both workflows have an optional **Use AI** toggle that sends the generated test cases through Ollama for enhancement before execution.

## Configuration

`src/main/resources/application.properties`

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `ollama.base-url` | `http://localhost:11434` | Ollama API endpoint |
| `ollama.model` | `qwen2.5-coder:7b` | Model used for AI enhancement |

## API

### Website testing
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/website/analyze` | Analyse a page and discover elements |
| POST | `/api/v1/website/generate-tests` | Generate test cases from a URL |
| POST | `/api/v1/website/generate-playwright` | Generate a Playwright `.spec.js` from a URL |
| POST | `/api/v1/website/run-playwright` | Run the generated Playwright script |
| GET | `/api/v1/website/playwright-status` | Check Playwright installation status |

### Swagger / OpenAPI
| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/swagger/parse` | Parse a Swagger 2.0 or OpenAPI 3.0 spec from a URL |
| POST | `/api/v1/swagger/generate-tests` | Generate test cases from a parsed spec |
| POST | `/api/v1/swagger/parse-and-generate` | Parse spec and generate tests in one call |
| POST | `/api/v1/swagger/run-tests` | Run the generated API tests |

### AI agent (requires Ollama)
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/agent/status` | Check Ollama availability |
| GET | `/api/v1/agent/models` | List available Ollama models |
| POST | `/api/v1/agent/enhance` | Enhance generated test cases using AI |
| POST | `/api/v1/agent/chat` | General-purpose chat with the configured model |
| GET | `/api/v1/agent/security-tests` | Generate security-focused test cases |
| GET | `/api/v1/agent/accessibility-tests` | Generate accessibility-focused test cases |

### Health
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/health` | Health check |
| GET | `/api/v1/info` | App info |

## Generated output

| Path | Contents |
|---|---|
| `generated-playwright-tests/` | Generated `.spec.js` files + Playwright config |
| `playwright-report/` | HTML test report after a test run |

## Stack

- Spring Boot 3.2.4 (MVC + WebFlux + WebSocket)
- Playwright Java 1.42.0 (server-side test execution)
- swagger-parser 2.1.22 (Swagger 2.0 + OpenAPI 3.0)
- Jsoup 1.17.2 (page element discovery)
- Ollama (local LLM, optional)
- Frontend: plain HTML/JS/CSS (`src/main/resources/static/`)
