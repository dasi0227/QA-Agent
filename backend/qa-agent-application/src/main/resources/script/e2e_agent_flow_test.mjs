#!/usr/bin/env node

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const EXIT_CODE = {
    SUCCESS: 0,
    BUSINESS_FAIL: 1,
    ENV_FAIL: 2,
    TIMEOUT_FAIL: 3
};

const ERROR_TYPE = {
    ENV_STARTUP: 'ENV_STARTUP',
    AUTH: 'AUTH',
    INDEX_TIMEOUT: 'INDEX_TIMEOUT',
    SSE_TERMINAL_FAIL: 'SSE_TERMINAL_FAIL',
    BUSINESS_4XX_5XX: 'BUSINESS_4XX_5XX',
    DATA_INCONSISTENT: 'DATA_INCONSISTENT',
    TIMEOUT: 'TIMEOUT',
    UNKNOWN: 'UNKNOWN'
};

const ALLOWED_FEEDBACK_RESULT = new Set(['PERFECT', 'CORRECT', 'DEFICIENT', 'WRONG', 'UNKNOWN']);

class FlowFailure extends Error {
    constructor({ step, errorType, reason, evidence = {}, impact = '', exitCode = EXIT_CODE.BUSINESS_FAIL }) {
        super(reason);
        this.name = 'FlowFailure';
        this.step = step;
        this.errorType = errorType;
        this.reason = reason;
        this.evidence = evidence;
        this.impact = impact;
        this.exitCode = exitCode;
    }
}

function parseArgs(argv) {
    const args = {
        config: null,
        dryRun: false,
        baseUrl: null
    };

    for (let i = 0; i < argv.length; i++) {
        const token = argv[i];
        if (token === '--config') {
            args.config = argv[++i];
        } else if (token === '--dry-run') {
            args.dryRun = true;
        } else if (token === '--base-url') {
            args.baseUrl = argv[++i];
        } else {
            throw new FlowFailure({
                step: 'parse_args',
                errorType: ERROR_TYPE.ENV_STARTUP,
                reason: `未知参数: ${token}`,
                impact: '脚本无法启动',
                exitCode: EXIT_CODE.ENV_FAIL
            });
        }
    }

    return args;
}

function toBoolean(value, fallback) {
    if (value === undefined || value === null || value === '') {
        return fallback;
    }
    const normalized = String(value).trim().toLowerCase();
    if (normalized === 'true' || normalized === '1') {
        return true;
    }
    if (normalized === 'false' || normalized === '0') {
        return false;
    }
    return fallback;
}

function nowIso() {
    return new Date().toISOString();
}

function shortText(input, max = 500) {
    if (input === null || input === undefined) {
        return '';
    }
    const text = typeof input === 'string' ? input : JSON.stringify(input);
    if (text.length <= max) {
        return text;
    }
    return `${text.slice(0, max)}...`;
}

function ensure(value, failureFactory) {
    if (!value) {
        throw failureFactory();
    }
}

async function loadConfig(scriptDir, args) {
    const configPath = path.resolve(scriptDir, args.config ?? 'e2e_agent_flow.config.json');
    const raw = await fsp.readFile(configPath, 'utf8');
    const config = JSON.parse(raw);

    if (args.baseUrl) {
        config.baseUrl = args.baseUrl;
    }

    if (process.env.E2E_BASE_URL) {
        config.baseUrl = process.env.E2E_BASE_URL;
    }
    if (process.env.E2E_USERNAME) {
        config.auth.username = process.env.E2E_USERNAME;
    }
    if (process.env.E2E_PASSWORD) {
        config.auth.password = process.env.E2E_PASSWORD;
    }
    if (process.env.E2E_AUTO_STOP_ON_FINISH !== undefined) {
        config.runtime.autoStopOnFinish = toBoolean(process.env.E2E_AUTO_STOP_ON_FINISH, config.runtime.autoStopOnFinish);
    }
    if (process.env.E2E_AUTO_STOP_ON_FAILURE !== undefined) {
        config.runtime.autoStopOnFailure = toBoolean(process.env.E2E_AUTO_STOP_ON_FAILURE, config.runtime.autoStopOnFailure);
    }
    if (process.env.E2E_STOP_DOCKER_ON_STOP !== undefined) {
        config.runtime.stopDockerOnStopScript = toBoolean(process.env.E2E_STOP_DOCKER_ON_STOP, config.runtime.stopDockerOnStopScript);
    }

    const baseUrl = String(config.baseUrl || '').replace(/\/+$/, '');
    const notePath = path.resolve(scriptDir, config.sourceNote.path);
    const reportDir = path.resolve(scriptDir, config.report.dir);

    return {
        ...config,
        __meta: {
            configPath,
            scriptDir,
            baseUrl,
            notePath,
            reportDir
        }
    };
}

function validateConfig(config) {
    ensure(config.__meta.baseUrl, () => new FlowFailure({
        step: 'validate_config',
        errorType: ERROR_TYPE.ENV_STARTUP,
        reason: 'baseUrl 不能为空',
        impact: '无法发起接口请求',
        exitCode: EXIT_CODE.ENV_FAIL
    }));

    ensure(config.auth?.username && config.auth?.password, () => new FlowFailure({
        step: 'validate_config',
        errorType: ERROR_TYPE.ENV_STARTUP,
        reason: 'auth.username / auth.password 不能为空',
        impact: '无法登录',
        exitCode: EXIT_CODE.ENV_FAIL
    }));

    ensure(config.generate?.requestedQuestionCount === 20, () => new FlowFailure({
        step: 'validate_config',
        errorType: ERROR_TYPE.ENV_STARTUP,
        reason: 'requestedQuestionCount 必须固定为 20',
        impact: '不满足测试目标',
        exitCode: EXIT_CODE.ENV_FAIL
    }));

    ensure(config.generate?.prompt, () => new FlowFailure({
        step: 'validate_config',
        errorType: ERROR_TYPE.ENV_STARTUP,
        reason: 'generate.prompt 不能为空',
        impact: '无法触发生成链路',
        exitCode: EXIT_CODE.ENV_FAIL
    }));

    ensure(fs.existsSync(config.__meta.notePath), () => new FlowFailure({
        step: 'validate_config',
        errorType: ERROR_TYPE.ENV_STARTUP,
        reason: `资料文件不存在: ${config.__meta.notePath}`,
        impact: '无法上传资料',
        exitCode: EXIT_CODE.ENV_FAIL
    }));
}

function createState(config, args) {
    const runId = `run-${Date.now()}`;
    return {
        runId,
        startedAt: nowIso(),
        endedAt: null,
        args,
        configSnapshot: {
            baseUrl: config.__meta.baseUrl,
            username: config.auth.username,
            notePath: config.__meta.notePath,
            requestedQuestionCount: config.generate.requestedQuestionCount,
            prompt: config.generate.prompt,
            timeout: config.timeouts,
            polling: config.polling,
            runtime: config.runtime,
            dryRun: args.dryRun
        },
        steps: [],
        artifacts: {},
        result: {
            status: 'RUNNING',
            exitCode: null,
            errorType: null,
            reason: null,
            impact: null
        }
    };
}

async function writeReport(config, state) {
    await fsp.mkdir(config.__meta.reportDir, { recursive: true });

    const finishedAt = nowIso();
    state.endedAt = finishedAt;
    const payload = {
        ...state,
        finishedAt,
        durationMs: Date.now() - new Date(state.startedAt).getTime()
    };

    const latestPath = path.join(config.__meta.reportDir, 'latest.json');
    const archivePath = path.join(config.__meta.reportDir, `${state.runId}.json`);

    const content = JSON.stringify(payload, null, 4);
    await fsp.writeFile(latestPath, content, 'utf8');
    await fsp.writeFile(archivePath, content, 'utf8');

    return { latestPath, archivePath };
}

async function runStep(state, step, action) {
    const startedAt = Date.now();
    try {
        const data = await action();
        state.steps.push({
            step,
            status: 'SUCCESS',
            startedAt: new Date(startedAt).toISOString(),
            endedAt: nowIso(),
            durationMs: Date.now() - startedAt,
            evidence: summarizeForStep(step, data)
        });
        return data;
    } catch (error) {
        const failure = normalizeFailure(step, error);
        state.steps.push({
            step,
            status: 'FAILED',
            startedAt: new Date(startedAt).toISOString(),
            endedAt: nowIso(),
            durationMs: Date.now() - startedAt,
            errorType: failure.errorType,
            reason: failure.reason,
            evidence: failure.evidence,
            impact: failure.impact
        });
        throw failure;
    }
}

function normalizeFailure(step, error) {
    if (error instanceof FlowFailure) {
        return error;
    }
    if (error?.name === 'AbortError') {
        return new FlowFailure({
            step,
            errorType: ERROR_TYPE.TIMEOUT,
            reason: `${step} 请求超时`,
            evidence: { message: String(error?.message || '') },
            impact: '当前步骤超时，流程中止',
            exitCode: EXIT_CODE.TIMEOUT_FAIL
        });
    }
    return new FlowFailure({
        step,
        errorType: ERROR_TYPE.UNKNOWN,
        reason: error?.message || String(error),
        evidence: { stack: shortText(error?.stack || '') },
        impact: '发生未分类错误，流程中止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    });
}

function summarizeForStep(step, data) {
    if (!data) {
        return {};
    }
    if (step === 'login') {
        return { userId: data.userId, username: data.username };
    }
    if (step === 'upload_source_note') {
        return { documentId: data.id, fileName: data.fileName };
    }
    if (step === 'generate_qa_set_sse') {
        return {
            taskId: data.taskId,
            events: data.events.length,
            terminalStatus: data.terminalEvent?.status,
            terminalCompleted: data.terminalEvent?.isCompleted
        };
    }
    if (step === 'query_task_status') {
        return { taskId: data.taskId, status: data.status, qaSetId: data.qaSetId };
    }
    if (step === 'query_qa_items') {
        return { count: data.length };
    }
    if (step === 'create_practice_session') {
        return { sessionId: data.id, totalQuestions: data.totalQuestions };
    }
    if (step === 'feedback_all_items') {
        return { count: data.length };
    }
    if (step === 'assess_session') {
        return {
            sessionId: data.sessionId,
            score: data.score,
            accuracy: data.accuracy,
            correctCount: data.correctCount,
            deficientCount: data.deficientCount,
            wrongCount: data.wrongCount,
            unknownCount: data.unknownCount
        };
    }
    return { preview: shortText(data, 280) };
}

async function fetchWithTimeout(url, options, timeoutMs) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(new Error('timeout')), timeoutMs);
    try {
        return await fetch(url, { ...options, signal: controller.signal });
    } finally {
        clearTimeout(timer);
    }
}

async function parseJsonResponse(res) {
    const raw = await res.text();
    if (!raw) {
        return null;
    }
    try {
        return JSON.parse(raw);
    } catch (error) {
        throw new FlowFailure({
            step: 'parse_json_response',
            errorType: ERROR_TYPE.BUSINESS_4XX_5XX,
            reason: '响应不是合法 JSON',
            evidence: { status: res.status, body: shortText(raw) },
            impact: '无法解析接口响应',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        });
    }
}

function ensureResultSuccess(step, httpStatus, payload) {
    if (!payload || typeof payload !== 'object' || !Object.prototype.hasOwnProperty.call(payload, 'code')) {
        throw new FlowFailure({
            step,
            errorType: ERROR_TYPE.BUSINESS_4XX_5XX,
            reason: '响应缺少 Result 包装结构',
            evidence: { httpStatus, payload: shortText(payload) },
            impact: '接口契约不符合预期',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        });
    }

    if (payload.code !== 0) {
        const isAuthError = step === 'login' || step === 'update_profile';
        throw new FlowFailure({
            step,
            errorType: isAuthError ? ERROR_TYPE.AUTH : ERROR_TYPE.BUSINESS_4XX_5XX,
            reason: `业务返回失败 code=${payload.code}, msg=${payload.msg}`,
            evidence: { httpStatus, payload },
            impact: '当前步骤失败，流程立即停止',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        });
    }

    return payload.data;
}

async function callResultApi({ step, method, url, token, body, timeoutMs }) {
    const headers = {
        'Content-Type': 'application/json'
    };
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    const res = await fetchWithTimeout(url, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body)
    }, timeoutMs);

    const payload = await parseJsonResponse(res);
    return ensureResultSuccess(step, res.status, payload);
}

async function sleep(ms) {
    await new Promise((resolve) => setTimeout(resolve, ms));
}

function buildRunTitle(prefix) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    return `${prefix}-${timestamp}`;
}

function buildFeedbackInput(qaItem, index) {
    const isUnknown = index % 5 === 0;
    if (isUnknown) {
        return {
            userAnswer: '',
            unknown: true,
            mode: 'UNKNOWN'
        };
    }

    if (index % 2 === 0) {
        const answer = (qaItem.answer || '').trim();
        return {
            userAnswer: answer.length > 180 ? answer.slice(0, 180) : answer,
            unknown: false,
            mode: 'GOOD'
        };
    }

    return {
        userAnswer: '这是自动化测试中的故意不完整回答，只覆盖少量关键点。',
        unknown: false,
        mode: 'WEAK'
    };
}

async function parseSseStream(response, timeoutMs) {
    ensure(response.body, () => new FlowFailure({
        step: 'generate_qa_set_sse',
        errorType: ERROR_TYPE.SSE_TERMINAL_FAIL,
        reason: 'SSE 响应体为空',
        impact: '无法获取任务事件流',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf8');

    let buffer = '';
    const events = [];
    let taskId = '';

    const startedAt = Date.now();

    while (true) {
        if (Date.now() - startedAt > timeoutMs) {
            throw new FlowFailure({
                step: 'generate_qa_set_sse',
                errorType: ERROR_TYPE.TIMEOUT,
                reason: `SSE 超时 (${timeoutMs}ms)`,
                evidence: { receivedEvents: events.length, taskId },
                impact: '无法确认生成终态，流程终止',
                exitCode: EXIT_CODE.TIMEOUT_FAIL
            });
        }

        const { done, value } = await reader.read();
        if (done) {
            break;
        }

        buffer += decoder.decode(value, { stream: true });

        let separatorIndex = buffer.search(/\r?\n\r?\n/);
        while (separatorIndex >= 0) {
            const block = buffer.slice(0, separatorIndex);
            buffer = buffer.slice(separatorIndex).replace(/^\r?\n\r?\n/, '');

            const parsed = parseSseBlock(block);
            if (parsed) {
                events.push(parsed);
                if (!taskId && parsed.taskId) {
                    taskId = parsed.taskId;
                }
            }

            separatorIndex = buffer.search(/\r?\n\r?\n/);
        }
    }

    const terminalStatuses = new Set(['SOLVED', 'UNSOLVED', 'CANCELED']);
    const terminalEvent = events.findLast((item) => {
        if (!item) {
            return false;
        }
        return item.isCompleted === true || terminalStatuses.has(String(item.status || '').toUpperCase());
    }) || null;

    ensure(terminalEvent, () => new FlowFailure({
        step: 'generate_qa_set_sse',
        errorType: ERROR_TYPE.SSE_TERMINAL_FAIL,
        reason: 'SSE 未接收到终态事件',
        evidence: { receivedEvents: events.length, taskId },
        impact: '无法确认是否生成成功，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    ensure(String(terminalEvent.status || '').toUpperCase() === 'SOLVED' && terminalEvent.isCompleted === true, () => new FlowFailure({
        step: 'generate_qa_set_sse',
        errorType: ERROR_TYPE.SSE_TERMINAL_FAIL,
        reason: `SSE 终态不符合预期: status=${terminalEvent.status}, isCompleted=${terminalEvent.isCompleted}`,
        evidence: {
            taskId,
            terminalEvent,
            lastEvent: events.at(-1) || null
        },
        impact: '生成任务未成功完成，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    return {
        taskId,
        events,
        terminalEvent
    };
}

function parseSseBlock(block) {
    if (!block) {
        return null;
    }

    const lines = block.split(/\r?\n/);
    const dataLines = [];

    for (const line of lines) {
        if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).trim());
        }
    }

    if (dataLines.length === 0) {
        return null;
    }

    const joined = dataLines.join('\n').trim();
    if (!joined) {
        return null;
    }

    try {
        const parsed = JSON.parse(joined);
        if (!parsed || typeof parsed !== 'object') {
            return null;
        }

        // Some serializers expose this field as `completed` instead of `isCompleted`.
        if (parsed.isCompleted === undefined && parsed.completed !== undefined) {
            parsed.isCompleted = Boolean(parsed.completed);
        } else if (parsed.isCompleted !== undefined) {
            parsed.isCompleted = Boolean(parsed.isCompleted);
        }

        return parsed;
    } catch {
        return null;
    }
}

function assertAssessInvariants(assessResponse, expectedTotal) {
    const counts = [
        assessResponse.correctCount,
        assessResponse.deficientCount,
        assessResponse.wrongCount,
        assessResponse.unknownCount
    ];

    if (counts.some((value) => !Number.isInteger(value) || value < 0)) {
        throw new FlowFailure({
            step: 'assert_assess_invariants',
            errorType: ERROR_TYPE.DATA_INCONSISTENT,
            reason: 'Assess 分布字段不合法',
            evidence: { assessResponse },
            impact: '评估结果不可用，流程终止',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        });
    }

    const sum = counts.reduce((acc, cur) => acc + cur, 0);
    if (sum !== expectedTotal) {
        throw new FlowFailure({
            step: 'assert_assess_invariants',
            errorType: ERROR_TYPE.DATA_INCONSISTENT,
            reason: `Assess 结果统计不一致: sum=${sum}, expected=${expectedTotal}`,
            evidence: { assessResponse },
            impact: '评估统计异常，流程终止',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        });
    }

    const score = assessResponse.score;
    const accuracy = Number(assessResponse.accuracy);
    if (!Number.isFinite(score) || score < 0 || score > 100) {
        throw new FlowFailure({
            step: 'assert_assess_invariants',
            errorType: ERROR_TYPE.DATA_INCONSISTENT,
            reason: `Assess score 越界: ${score}`,
            evidence: { assessResponse },
            impact: '评估结果异常，流程终止',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        });
    }
    if (!Number.isFinite(accuracy) || accuracy < 0 || accuracy > 100) {
        throw new FlowFailure({
            step: 'assert_assess_invariants',
            errorType: ERROR_TYPE.DATA_INCONSISTENT,
            reason: `Assess accuracy 越界: ${assessResponse.accuracy}`,
            evidence: { assessResponse },
            impact: '评估结果异常，流程终止',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        });
    }

    ensure(assessResponse.assessDetail?.overallComment && assessResponse.assessDetail?.reviewGuidance, () => new FlowFailure({
        step: 'assert_assess_invariants',
        errorType: ERROR_TYPE.DATA_INCONSISTENT,
        reason: 'Assess 详情缺少 overallComment 或 reviewGuidance',
        evidence: { assessResponse },
        impact: '评估详情不完整，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));
}

async function maybeStopBackend(config, reasonLabel) {
    const stopScript = path.resolve(config.__meta.scriptDir, 'stop_backend.sh');
    if (!fs.existsSync(stopScript)) {
        return;
    }

    await new Promise((resolve, reject) => {
        const child = spawn(stopScript, [], {
            stdio: 'inherit',
            env: {
                ...process.env,
                STOP_DOCKER_STACK: String(config.runtime.stopDockerOnStopScript)
            }
        });
        child.on('error', reject);
        child.on('exit', (code) => {
            if (code === 0) {
                resolve();
            } else {
                reject(new Error(`stop_backend.sh 执行失败，退出码=${code} (${reasonLabel})`));
            }
        });
    });
}

async function runFlow(config, state) {
    const { baseUrl } = config.__meta;
    const requestMs = config.timeouts.requestMs;

    let token = '';
    let uploadedDocumentId = '';
    let generateTaskId = '';
    let generatedQaSetId = '';
    let practiceSessionId = '';

    const loginData = await runStep(state, 'login', async () => {
        const url = `${baseUrl}/auth/login`;
        return callResultApi({
            step: 'login',
            method: 'POST',
            url,
            body: {
                username: config.auth.username,
                password: config.auth.password
            },
            timeoutMs: requestMs
        });
    });

    token = loginData.accessToken;
    ensure(token, () => new FlowFailure({
        step: 'login',
        errorType: ERROR_TYPE.AUTH,
        reason: '登录成功但 accessToken 为空',
        evidence: { loginData },
        impact: '后续接口无法鉴权',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    await runStep(state, 'update_profile', async () => {
        const url = `${baseUrl}/identity/profile/update`;
        return callResultApi({
            step: 'update_profile',
            method: 'POST',
            url,
            token,
            body: {
                allowGeneralKnowledge: config.profile.allowGeneralKnowledge,
                allowWebSearch: config.profile.allowWebSearch
            },
            timeoutMs: requestMs
        });
    });

    const sourceContent = await runStep(state, 'read_source_note', async () => {
        const content = await fsp.readFile(config.__meta.notePath, 'utf8');
        ensure(content && content.trim().length > 0, () => new FlowFailure({
            step: 'read_source_note',
            errorType: ERROR_TYPE.ENV_STARTUP,
            reason: `资料文件为空: ${config.__meta.notePath}`,
            impact: '无法上传资料',
            exitCode: EXIT_CODE.ENV_FAIL
        }));
        return content;
    });

    const uploadData = await runStep(state, 'upload_source_note', async () => {
        const url = `${baseUrl}/document/source/upload`;
        const fileName = `OOP-${state.runId}.md`;
        const response = await callResultApi({
            step: 'upload_source_note',
            method: 'POST',
            url,
            token,
            body: {
                fileName,
                fileType: config.sourceNote.fileType,
                filePath: config.__meta.notePath,
                rawContent: sourceContent
            },
            timeoutMs: requestMs
        });

        ensure(response?.id, () => new FlowFailure({
            step: 'upload_source_note',
            errorType: ERROR_TYPE.BUSINESS_4XX_5XX,
            reason: '上传成功但未返回 documentId',
            evidence: { response },
            impact: '后续无法进行检索与生成',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        }));

        return response;
    });

    uploadedDocumentId = uploadData.id;
    state.artifacts.documentId = uploadedDocumentId;

    await runStep(state, 'wait_index_ready', async () => {
        const deadline = Date.now() + config.timeouts.indexWaitMs;
        const url = `${baseUrl}/document/source/search`;
        const queryText = '封装 继承 多态 interface static';

        while (Date.now() < deadline) {
            const data = await callResultApi({
                step: 'wait_index_ready',
                method: 'POST',
                url,
                token,
                body: {
                    queryText,
                    filterDocumentIds: [uploadedDocumentId]
                },
                timeoutMs: requestMs
            });

            if (Array.isArray(data) && data.length > 0) {
                return { searchHits: data.length };
            }

            await sleep(config.polling.indexIntervalMs);
        }

        throw new FlowFailure({
            step: 'wait_index_ready',
            errorType: ERROR_TYPE.INDEX_TIMEOUT,
            reason: `等待索引超时 (${config.timeouts.indexWaitMs}ms)`,
            evidence: { documentId: uploadedDocumentId },
            impact: '无法确认资料可检索，流程终止',
            exitCode: EXIT_CODE.TIMEOUT_FAIL
        });
    });

    const generateSseResult = await runStep(state, 'generate_qa_set_sse', async () => {
        const url = `${baseUrl}/qa/set/create`;
        const response = await fetchWithTimeout(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`
            },
            body: JSON.stringify({
                title: buildRunTitle(config.generate.titlePrefix),
                userPrompt: config.generate.prompt,
                jobDescription: config.generate.jobDescription,
                documentIds: [uploadedDocumentId],
                requestedQuestionCount: config.generate.requestedQuestionCount
            })
        }, config.timeouts.sseMs + 5000);

        if (!response.ok) {
            const raw = await response.text();
            throw new FlowFailure({
                step: 'generate_qa_set_sse',
                errorType: ERROR_TYPE.BUSINESS_4XX_5XX,
                reason: `发起生成失败 HTTP ${response.status}`,
                evidence: { body: shortText(raw) },
                impact: '生成链路未启动，流程终止',
                exitCode: EXIT_CODE.BUSINESS_FAIL
            });
        }

        return parseSseStream(response, config.timeouts.sseMs);
    });

    generateTaskId = generateSseResult.taskId;
    state.artifacts.taskId = generateTaskId;

    ensure(generateTaskId, () => new FlowFailure({
        step: 'generate_qa_set_sse',
        errorType: ERROR_TYPE.SSE_TERMINAL_FAIL,
        reason: 'SSE 解析完成但 taskId 为空',
        evidence: { terminalEvent: generateSseResult.terminalEvent },
        impact: '无法查询任务状态，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    const taskStatus = await runStep(state, 'query_task_status', async () => {
        const url = `${baseUrl}/qa/set/task-status?taskId=${encodeURIComponent(generateTaskId)}`;
        return callResultApi({
            step: 'query_task_status',
            method: 'GET',
            url,
            token,
            timeoutMs: requestMs
        });
    });

    ensure(taskStatus.status === 'SOLVED', () => new FlowFailure({
        step: 'query_task_status',
        errorType: ERROR_TYPE.SSE_TERMINAL_FAIL,
        reason: `任务终态不是 SOLVED: ${taskStatus.status}`,
        evidence: { taskStatus },
        impact: '生成链路未成功，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    generatedQaSetId = taskStatus.qaSetId;
    state.artifacts.qaSetId = generatedQaSetId;

    ensure(generatedQaSetId, () => new FlowFailure({
        step: 'query_task_status',
        errorType: ERROR_TYPE.DATA_INCONSISTENT,
        reason: '任务状态缺少 qaSetId',
        evidence: { taskStatus },
        impact: '无法进入练习链路，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    const qaItems = await runStep(state, 'query_qa_items', async () => {
        const url = `${baseUrl}/qa/item/query`;
        const data = await callResultApi({
            step: 'query_qa_items',
            method: 'POST',
            url,
            token,
            body: {
                qaSetId: generatedQaSetId
            },
            timeoutMs: requestMs
        });

        ensure(Array.isArray(data), () => new FlowFailure({
            step: 'query_qa_items',
            errorType: ERROR_TYPE.DATA_INCONSISTENT,
            reason: 'qa/item/query 返回不是数组',
            evidence: { data },
            impact: '无法继续练习链路',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        }));

        return data;
    });

    ensure(qaItems.length === config.generate.requestedQuestionCount, () => new FlowFailure({
        step: 'query_qa_items',
        errorType: ERROR_TYPE.DATA_INCONSISTENT,
        reason: `题目数量异常: expected=${config.generate.requestedQuestionCount}, actual=${qaItems.length}`,
        evidence: { qaSetId: generatedQaSetId },
        impact: '生成结果不满足测试目标，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    const createdSession = await runStep(state, 'create_practice_session', async () => {
        const url = `${baseUrl}/practice/session/create`;
        return callResultApi({
            step: 'create_practice_session',
            method: 'POST',
            url,
            token,
            body: {
                qaSetId: generatedQaSetId,
                mode: 'STANDARD',
                feedbackMode: 'IMMEDIATE',
                status: 'IN_PROGRESS',
                totalQuestions: config.generate.requestedQuestionCount,
                answeredCount: 0
            },
            timeoutMs: requestMs
        });
    });

    practiceSessionId = createdSession.id;
    state.artifacts.sessionId = practiceSessionId;

    ensure(practiceSessionId, () => new FlowFailure({
        step: 'create_practice_session',
        errorType: ERROR_TYPE.DATA_INCONSISTENT,
        reason: '创建练习会话后 sessionId 为空',
        evidence: { createdSession },
        impact: '无法进入 feedback 链路',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    const sortedQaItems = [...qaItems].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
    const sessionItems = await runStep(state, 'create_practice_session_items', async () => {
        const url = `${baseUrl}/practice/session-item/create`;
        const items = [];
        for (let i = 0; i < sortedQaItems.length; i++) {
            const qaItem = sortedQaItems[i];
            const created = await callResultApi({
                step: 'create_practice_session_items',
                method: 'POST',
                url,
                token,
                body: {
                    sessionId: practiceSessionId,
                    qaItemId: qaItem.id,
                    sortOrder: qaItem.sortOrder || i + 1
                },
                timeoutMs: requestMs
            });
            items.push({
                sessionItem: created,
                qaItem
            });
        }
        return items;
    });

    const feedbackResponses = await runStep(state, 'feedback_all_items', async () => {
        const url = `${baseUrl}/practice/session-item/feedback`;
        const responses = [];

        for (let i = 0; i < sessionItems.length; i++) {
            const { sessionItem, qaItem } = sessionItems[i];
            const input = buildFeedbackInput(qaItem, i);

            const feedback = await callResultApi({
                step: 'feedback_all_items',
                method: 'POST',
                url,
                token,
                body: {
                    sessionItemId: sessionItem.id,
                    userAnswer: input.userAnswer,
                    unknown: input.unknown
                },
                timeoutMs: requestMs
            });

            ensure(ALLOWED_FEEDBACK_RESULT.has(feedback.result), () => new FlowFailure({
                step: 'feedback_all_items',
                errorType: ERROR_TYPE.DATA_INCONSISTENT,
                reason: `反馈 result 非法: ${feedback.result}`,
                evidence: { feedback },
                impact: '反馈结果不可用，流程终止',
                exitCode: EXIT_CODE.BUSINESS_FAIL
            }));

            ensure(Number.isInteger(feedback.score) && feedback.score >= 0 && feedback.score <= 100, () => new FlowFailure({
                step: 'feedback_all_items',
                errorType: ERROR_TYPE.DATA_INCONSISTENT,
                reason: `反馈 score 越界: ${feedback.score}`,
                evidence: { feedback },
                impact: '反馈结果不可用，流程终止',
                exitCode: EXIT_CODE.BUSINESS_FAIL
            }));

            ensure(feedback.answeredAt, () => new FlowFailure({
                step: 'feedback_all_items',
                errorType: ERROR_TYPE.DATA_INCONSISTENT,
                reason: '反馈结果缺少 answeredAt',
                evidence: { feedback },
                impact: '反馈结果不完整，流程终止',
                exitCode: EXIT_CODE.BUSINESS_FAIL
            }));

            responses.push(feedback);
        }

        return responses;
    });

    ensure(feedbackResponses.length === config.generate.requestedQuestionCount, () => new FlowFailure({
        step: 'feedback_all_items',
        errorType: ERROR_TYPE.DATA_INCONSISTENT,
        reason: `feedback 数量异常: expected=${config.generate.requestedQuestionCount}, actual=${feedbackResponses.length}`,
        evidence: {},
        impact: '反馈链路不完整，流程终止',
        exitCode: EXIT_CODE.BUSINESS_FAIL
    }));

    await runStep(state, 'check_session_answered_count', async () => {
        const url = `${baseUrl}/practice/session/detail?id=${encodeURIComponent(practiceSessionId)}`;
        const session = await callResultApi({
            step: 'check_session_answered_count',
            method: 'GET',
            url,
            token,
            timeoutMs: requestMs
        });

        ensure(session.answeredCount === config.generate.requestedQuestionCount, () => new FlowFailure({
            step: 'check_session_answered_count',
            errorType: ERROR_TYPE.DATA_INCONSISTENT,
            reason: `answeredCount 异常: expected=${config.generate.requestedQuestionCount}, actual=${session.answeredCount}`,
            evidence: { session },
            impact: '会话统计异常，流程终止',
            exitCode: EXIT_CODE.BUSINESS_FAIL
        }));

        return session;
    });

    const assessResponse = await runStep(state, 'assess_session', async () => {
        const url = `${baseUrl}/practice/session/assess`;
        return callResultApi({
            step: 'assess_session',
            method: 'POST',
            url,
            token,
            body: {
                sessionId: practiceSessionId
            },
            timeoutMs: requestMs
        });
    });

    await runStep(state, 'assert_assess_invariants', async () => {
        assertAssessInvariants(assessResponse, config.generate.requestedQuestionCount);
        return { ok: true };
    });
}

async function runDryMode(config, state) {
    await runStep(state, 'dry_run_validate_paths', async () => {
        const checks = {
            configPath: config.__meta.configPath,
            notePath: config.__meta.notePath,
            reportDir: config.__meta.reportDir,
            exists: {
                config: fs.existsSync(config.__meta.configPath),
                note: fs.existsSync(config.__meta.notePath)
            }
        };

        ensure(checks.exists.config && checks.exists.note, () => new FlowFailure({
            step: 'dry_run_validate_paths',
            errorType: ERROR_TYPE.ENV_STARTUP,
            reason: 'dry-run 路径校验失败',
            evidence: checks,
            impact: '请先修复配置后再执行正式测试',
            exitCode: EXIT_CODE.ENV_FAIL
        }));

        return checks;
    });
}

function printSummary(state, reportPaths) {
    const success = state.result.status === 'SUCCESS';
    console.log('==========================================');
    console.log(`E2E 脚本执行${success ? '成功' : '失败'}`);
    console.log(`runId: ${state.runId}`);
    console.log(`status: ${state.result.status}`);
    console.log(`exitCode: ${state.result.exitCode}`);

    if (!success) {
        console.log(`errorType: ${state.result.errorType}`);
        console.log(`reason: ${state.result.reason}`);
        if (state.result.impact) {
            console.log(`impact: ${state.result.impact}`);
        }
    }

    console.log('artifacts:', JSON.stringify(state.artifacts, null, 2));
    console.log(`report(latest): ${reportPaths.latestPath}`);
    console.log(`report(archive): ${reportPaths.archivePath}`);
    console.log('==========================================');
}

async function main() {
    const scriptDir = path.dirname(fileURLToPath(import.meta.url));
    const args = parseArgs(process.argv.slice(2));

    const config = await loadConfig(scriptDir, args);
    validateConfig(config);

    const state = createState(config, args);

    try {
        if (args.dryRun) {
            await runDryMode(config, state);
        } else {
            await runFlow(config, state);
        }

        state.result = {
            status: 'SUCCESS',
            exitCode: EXIT_CODE.SUCCESS,
            errorType: null,
            reason: null,
            impact: null
        };

        if (!args.dryRun && config.runtime.autoStopOnFinish) {
            await maybeStopBackend(config, 'finish');
        }
    } catch (error) {
        const failure = normalizeFailure('main', error);
        state.result = {
            status: 'FAILED',
            exitCode: failure.exitCode,
            errorType: failure.errorType,
            reason: failure.reason,
            impact: failure.impact
        };

        if (!args.dryRun && config.runtime.autoStopOnFailure) {
            try {
                await maybeStopBackend(config, 'failure');
            } catch (stopError) {
                state.steps.push({
                    step: 'auto_stop_backend',
                    status: 'FAILED',
                    startedAt: nowIso(),
                    endedAt: nowIso(),
                    durationMs: 0,
                    errorType: ERROR_TYPE.ENV_STARTUP,
                    reason: stopError.message,
                    evidence: {},
                    impact: '自动收尾失败，请手动执行 stop_backend.sh'
                });
            }
        }
    }

    const reportPaths = await writeReport(config, state);
    printSummary(state, reportPaths);
    process.exit(state.result.exitCode ?? EXIT_CODE.BUSINESS_FAIL);
}

main().catch(async (error) => {
    console.error('脚本未捕获异常:', error);
    process.exit(EXIT_CODE.BUSINESS_FAIL);
});
