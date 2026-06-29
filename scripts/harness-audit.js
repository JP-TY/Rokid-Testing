#!/usr/bin/env node
/**
 * Harness Audit — deterministic repo scanner for ECC harness quality.
 * Rubric version: 2026-05-19
 *
 * Usage:
 *   node scripts/harness-audit.js [scope] [--format text|json] [--root <path>]
 *     scope: repo (default), hooks, skills, commands, agents
 */
const fs = require('fs');
const path = require('path');
const os = require('os');

// --- Argument parsing ---
const ROOT = process.cwd();
const SCOPE = (process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : 'repo').toLowerCase();
const FORMAT = process.argv.includes('--format')
  ? process.argv[process.argv.indexOf('--format') + 1] || 'text'
  : 'text';
const ROOT_ARG_IDX = process.argv.indexOf('--root');
const AUDIT_ROOT = ROOT_ARG_IDX >= 0 ? path.resolve(process.argv[ROOT_ARG_IDX + 1]) : ROOT;

const VALID_SCOPES = ['repo', 'hooks', 'skills', 'commands', 'agents'];
if (!VALID_SCOPES.includes(SCOPE)) {
  console.error(`Invalid scope "${SCOPE}". Valid: ${VALID_SCOPES.join(', ')}`);
  process.exit(1);
}

// --- Helpers ---
const HOME = os.homedir();

function exists(p) { try { fs.accessSync(p); return true; } catch { return false; } }
function readDir(p) { try { return fs.readdirSync(p); } catch { return []; } }
function readFile(p) { try { return fs.readFileSync(p, 'utf-8'); } catch { return ''; } }
function hasFile(p) { return exists(path.join(AUDIT_ROOT, p)); }
function isECCRepo() {
  return hasFile('ecc.json') || hasFile('.ecc') ||
    readDir(AUDIT_ROOT).some(d => d.startsWith('ecc-'));
}
function matchPattern(name, pattern) {
  // Convert glob-like pattern to regex
  const regexStr = pattern
    .replace(/[.+?^${}()|[\]\\]/g, '\\$&')  // escape regex special chars
    .replace(/\*/g, '.*');                     // * matches anything
  return new RegExp('^' + regexStr + '$').test(name);
}
function findWorkflows(root) {
  const found = [];
  function walk(dir) {
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (e.name === 'workflows' && path.basename(path.dirname(full)) === '.github') {
          const files = readDir(full).filter(f => f.endsWith('.yml') || f.endsWith('.yaml'));
          files.forEach(f => found.push(path.join(full, f)));
        } else if (e.name !== 'node_modules' && !e.name.endsWith('build') && e.name !== '.git') {
          walk(full);
        }
      }
    }
  }
  walk(root);
  return found;
}
function findTemplates(root) {
  const found = [];
  function walk(dir) {
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (e.name === 'ISSUE_TEMPLATE' && path.basename(path.dirname(full)) === '.github') {
          found.push(full);
        } else if (e.name !== 'node_modules' && !e.name.endsWith('build') && e.name !== '.git') {
          walk(full);
        }
      } else if (e.name === 'PULL_REQUEST_TEMPLATE.md' && path.basename(path.dirname(full)) === '.github') {
        found.push(full);
      }
    }
  }
  walk(root);
  return found;
}
function glob(root, pattern) {
  const results = [];
  function walk(dir) {
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (e.isDirectory() && e.name !== 'node_modules' && !e.name.endsWith('build') && e.name !== '.git') {
        walk(full);
      } else if (e.isFile() && matchPattern(e.name, pattern)) {
        results.push(full);
      }
    }
  }
  walk(root);
  return results;
}

// --- Paths ---
const USER_SKILLS = path.join(HOME, '.claude', 'skills');
const ECC_SKILLS  = path.join(USER_SKILLS, 'ecc');
const AGENT_SKILLS = path.join(HOME, '.agents', 'skills');
const USER_SKILL_DIR = ECC_SKILLS;
const HOOKS_CONFIG = path.join(HOME, '.claude', 'hooks.json');

// --- Detection ---
const isECC = isECCRepo();
const hasVercel     = hasFile('vercel.json') || exists(path.join(AUDIT_ROOT, '.vercel'));
const hasNetlify    = hasFile('netlify.toml') || exists(path.join(AUDIT_ROOT, '.netlify'));
const hasCloudflare = hasFile('wrangler.toml') || hasFile('wrangler.jsonc');
const hasFly        = hasFile('fly.toml');

// --- Applicable categories ---
const applicable = [
  'tool-coverage', 'context-efficiency', 'quality-gates',
  'memory-persistence', 'eval-coverage', 'security-guardrails',
  'cost-efficiency', 'github-integration',
];
if (hasVercel)     applicable.push('vercel-integration');
if (hasNetlify)    applicable.push('netlify-integration');
if (hasCloudflare) applicable.push('cloudflare-integration');
if (hasFly)        applicable.push('fly-integration');

// --- Shared state ---
const tcChecks = [], ceChecks = [], qgChecks = [], mpChecks = [];
const evChecks = [], sgChecks = [], costChecks = [], ghChecks = [];
const vercelChecks = [], netlifyChecks = [], cloudflareChecks = [], flyChecks = [];

let tcScore = 0, ceScore = 0, qgScore = 0, mpScore = 0;
let evScore = 0, sgScore = 0, costScore = 0, ghScore = 0;

// Agents.md detection (shared)
const agentsCandidates = [
  path.join(AUDIT_ROOT, 'AGENTS.md'),
  path.join(AUDIT_ROOT, 'workout-tracker', 'AGENTS.md'),
];
const agentsFound = agentsCandidates.filter(f => exists(f));

// ============================================================
// 1. TOOL COVERAGE (0-10)
// ============================================================
// MCP config
if (hasFile('.mcp.json') || hasFile('opencode.json')) {
  const mcp = readFile(path.join(AUDIT_ROOT, '.mcp.json'));
  const oc  = readFile(path.join(AUDIT_ROOT, 'opencode.json'));
  if (mcp.includes('mcpServers') || oc.includes('"mcp"')) {
    tcScore += 3;
    tcChecks.push({ pass: true, msg: 'MCP servers configured', file: '.mcp.json or opencode.json' });
  } else {
    tcChecks.push({ pass: false, msg: 'No MCP server configuration found', file: '.mcp.json' });
  }
} else {
  tcChecks.push({ pass: false, msg: 'No MCP config file', file: '.mcp.json' });
}

// OpenCode config
if (hasFile('opencode.json')) {
  tcScore += 2;
  tcChecks.push({ pass: true, msg: 'opencode.json present', file: 'opencode.json' });
} else {
  tcChecks.push({ pass: false, msg: 'No opencode.json', file: 'opencode.json' });
}

// Config directory items
if (exists(path.join(AUDIT_ROOT, 'config'))) {
  const cfgCount = readDir(path.join(AUDIT_ROOT, 'config')).filter(f => !f.startsWith('.')).length;
  tcScore += Math.min(cfgCount, 2);
  tcChecks.push({ pass: cfgCount > 0, msg: `Config directory with ${cfgCount} entries`, file: 'config/' });
}

// Project .opencode/ directory
if (exists(path.join(AUDIT_ROOT, '.opencode'))) {
  const ocCount = readDir(path.join(AUDIT_ROOT, '.opencode')).length;
  tcScore += Math.min(ocCount, 3);
  tcChecks.push({ pass: ocCount > 0, msg: `.opencode/ directory with ${ocCount} items`, file: '.opencode/' });
} else {
  tcChecks.push({ pass: false, msg: 'No .opencode/ directory', file: '.opencode/' });
}

tcScore = Math.min(tcScore, 10);

// ============================================================
// 2. CONTEXT EFFICIENCY (0-10)
// ============================================================
// AGENTS.md
if (agentsFound.length > 0) {
  const size = fs.statSync(agentsFound[0]).size;
  ceScore += (size > 0 && size < 30000) ? 4 : 2;
  ceChecks.push({ pass: true, msg: `AGENTS.md (${(size/1024).toFixed(1)}KB)`, file: path.relative(AUDIT_ROOT, agentsFound[0]) });
} else {
  ceChecks.push({ pass: false, msg: 'No AGENTS.md found', file: 'AGENTS.md' });
}

// CLAUDE.md
if (hasFile('CLAUDE.md')) {
  ceScore += 3;
  ceChecks.push({ pass: true, msg: 'CLAUDE.md present', file: 'CLAUDE.md' });
} else {
  ceChecks.push({ pass: false, msg: 'No CLAUDE.md', file: 'CLAUDE.md' });
}

// ECC / agent skills installed
const skillDirs = [USER_SKILLS, ECC_SKILLS, AGENT_SKILLS].filter(f => exists(f));
let skillCount = 0;
for (const sd of skillDirs) {
  try {
    for (const item of fs.readdirSync(sd)) {
      if (fs.statSync(path.join(sd, item)).isDirectory()) skillCount++;
    }
  } catch {}
}
if (skillCount >= 5) ceScore += 3;
else if (skillCount > 0) ceScore += Math.min(skillCount, 3);
ceChecks.push({
  pass: skillCount > 0,
  msg: skillCount > 0 ? `${skillCount} skills installed` : 'No agent skills installed',
  file: '~/.claude/skills/ + ~/.agents/skills/',
});

ceScore = Math.min(ceScore, 10);

// ============================================================
// 3. QUALITY GATES (0-10)
// ============================================================
const lintScripts = glob(AUDIT_ROOT, '*lint*');
if (lintScripts.length > 0) {
  qgScore += 3;
  qgChecks.push({ pass: true, msg: 'Lint script found', file: path.relative(AUDIT_ROOT, lintScripts[0]) });
} else {
  qgChecks.push({ pass: false, msg: 'No lint script', file: 'scripts/*lint*' });
}

// Build file (check root and immediate subdirectories)
const buildFiles = glob(AUDIT_ROOT, 'build.gradle.kts').concat(glob(AUDIT_ROOT, 'build.gradle'))
  .concat(glob(AUDIT_ROOT, 'package.json')).concat(glob(AUDIT_ROOT, 'Makefile'));
if (buildFiles.length > 0) {
  qgScore += 3;
  qgChecks.push({ pass: true, msg: `Build file present (${path.basename(path.dirname(buildFiles[0]))})`, file: path.relative(AUDIT_ROOT, buildFiles[0]) });
} else {
  qgChecks.push({ pass: false, msg: 'No build file', file: 'build.gradle.kts' });
}

// Test directory (walk subdirectories for test paths)
function dirExistsAnywhere(root, target) {
  let found = false;
  function walk(dir) {
    if (found) return;
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      if (e.name === target && e.isDirectory()) { found = true; return; }
      const full = path.join(dir, e.name);
      if (e.isDirectory() && e.name !== 'node_modules' && !e.name.endsWith('build') && e.name !== '.git') {
        walk(full);
      }
    }
  }
  walk(root);
  return found;
}
const testDirExists = dirExistsAnywhere(AUDIT_ROOT, 'test')
  || dirExistsAnywhere(AUDIT_ROOT, 'tests')
  || dirExistsAnywhere(AUDIT_ROOT, '__tests__')
  || dirExistsAnywhere(AUDIT_ROOT, 'androidTest');
qgScore += testDirExists ? 2 : 0;
qgChecks.push({
  pass: testDirExists,
  msg: testDirExists ? 'Test directory present' : 'No test directory',
  file: 'tests/ or app/src/test/',
});

// CI configuration (check root and subdirectories for .github/workflows)
const allWfFiles = findWorkflows(AUDIT_ROOT);
const wfCount = allWfFiles.length;
if (wfCount > 0) {
  qgScore += Math.min(wfCount * 2, 2);
  qgChecks.push({ pass: true, msg: `${wfCount} GitHub workflow(s)`, file: path.relative(AUDIT_ROOT, path.dirname(allWfFiles[0])) });
} else if (hasFile('Jenkinsfile') || hasFile('.gitlab-ci.yml')) {
  qgScore += 2;
  qgChecks.push({ pass: true, msg: 'CI configuration present', file: 'Jenkinsfile or .gitlab-ci.yml' });
} else {
  qgChecks.push({ pass: false, msg: 'No CI configuration', file: '.github/workflows/' });
}

qgScore = Math.min(qgScore, 10);

// ============================================================
// 4. MEMORY PERSISTENCE (0-10)
// ============================================================
if (agentsFound.length > 0) mpScore += 3;
if (hasFile('CLAUDE.md')) mpScore += 2;

// .opencode/agents/
if (exists(path.join(AUDIT_ROOT, '.opencode', 'agents'))) {
  const agentCount = readDir(path.join(AUDIT_ROOT, '.opencode', 'agents')).length;
  mpScore += Math.min(agentCount, 3);
  mpChecks.push({ pass: true, msg: `${agentCount} agent definition(s)`, file: '.opencode/agents/' });
} else {
  mpChecks.push({ pass: false, msg: 'No .opencode/agents/', file: '.opencode/agents/' });
}

// Hook-based memory
if (exists(HOOKS_CONFIG)) {
  const hc = readFile(HOOKS_CONFIG);
  if (hc.includes('"post-tool"') || hc.includes('"stop"') || hc.includes('memory')) {
    mpScore += 2;
    mpChecks.push({ pass: true, msg: 'Memory/stop hooks in hooks.json', file: '~/.claude/hooks.json' });
  } else {
    mpChecks.push({ pass: false, msg: 'hooks.json exists but no post-tool/stop hooks', file: '~/.claude/hooks.json' });
  }
} else {
  mpChecks.push({ pass: false, msg: 'No hooks.json', file: '~/.claude/hooks.json' });
}

mpScore = Math.min(mpScore, 10);

// ============================================================
// 5. EVAL COVERAGE (0-10)
// ============================================================
const testFiles = glob(AUDIT_ROOT, '*.test.*')
  .concat(glob(AUDIT_ROOT, '*.spec.*'))
  .concat(glob(AUDIT_ROOT, '*Test.*'))
  .concat(glob(AUDIT_ROOT, '*Tests.*'));
if (testFiles.length > 0) {
  evScore += Math.min(testFiles.length * 2, 4);
  evChecks.push({ pass: true, msg: `${testFiles.length} test file(s)`, file: path.basename(testFiles[0]) });
} else {
  evChecks.push({ pass: false, msg: 'No test files found', file: '*.test.*' });
}

// Coverage config (JS or Android/JaCoCo)
const coverageConfigs = glob(AUDIT_ROOT, 'jest.config.*')
  .concat(glob(AUDIT_ROOT, 'vitest.config.*'))
  .concat(glob(AUDIT_ROOT, '.coveragerc'))
  .concat(glob(AUDIT_ROOT, 'eval-coverage.json'));
if (coverageConfigs.length > 0) {
  evScore += 3;
  evChecks.push({ pass: true, msg: 'Test coverage config present', file: path.relative(AUDIT_ROOT, coverageConfigs[0]) });
} else {
  evChecks.push({ pass: false, msg: 'No coverage config', file: 'jest.config.* or eval-coverage.json' });
}

// Eval scripts
const evalScripts = glob(AUDIT_ROOT, '*eval*');
if (evalScripts.length > 0) {
  evScore += 3;
  evChecks.push({ pass: true, msg: `${evalScripts.length} eval script(s)`, file: path.relative(AUDIT_ROOT, evalScripts[0]) });
} else {
  evChecks.push({ pass: false, msg: 'No eval scripts', file: '*eval*' });
}

evScore = Math.min(evScore, 10);

// ============================================================
// 6. SECURITY GUARDRAILS (0-10)
// ============================================================
if (exists(HOOKS_CONFIG)) {
  const hc = readFile(HOOKS_CONFIG);
  if (hc.includes('pre-') || hc.includes('guard') || hc.includes('prompt') || hc.includes('preflight')) {
    sgScore += 4;
    sgChecks.push({ pass: true, msg: 'Preflight security hooks in hooks.json', file: '~/.claude/hooks.json' });
  } else {
    sgChecks.push({ pass: false, msg: 'hooks.json exists but no preflight/guard hooks', file: '~/.claude/hooks.json' });
  }
} else {
  sgChecks.push({ pass: false, msg: 'No hooks.json', file: '~/.claude/hooks.json' });
}

// .env template (check root and subdirectories)
const envTemplates = glob(AUDIT_ROOT, '.env.example').concat(glob(AUDIT_ROOT, '.env.template')).concat(glob(AUDIT_ROOT, '.env.sample'));
if (envTemplates.length > 0) {
  sgScore += 2;
  sgChecks.push({ pass: true, msg: 'Environment template present', file: path.relative(AUDIT_ROOT, envTemplates[0]) });
} else {
  sgChecks.push({ pass: false, msg: 'No environment template', file: '.env.example' });
}

// .gitignore with secrets
if (hasFile('.gitignore')) {
  const gi = readFile(path.join(AUDIT_ROOT, '.gitignore'));
  const hasSecrets = gi.includes('.env') || gi.includes('secret') || gi.includes('credential');
  sgScore += hasSecrets ? 2 : 1;
  sgChecks.push({ pass: true, msg: `.gitignore has ${hasSecrets ? 'secrets' : 'basic'} entries`, file: '.gitignore' });
} else {
  sgChecks.push({ pass: false, msg: 'No .gitignore', file: '.gitignore' });
}

// Security scan config
if (hasFile('security-scan.json') || hasFile('.agentshieldrc') || exists(path.join(HOME, '.claude', 'skills', 'ecc', 'security-scan'))) {
  sgScore += 2;
  sgChecks.push({ pass: true, msg: 'Security scan config or skill present', file: 'security-scan.json or ~/.claude/skills/ecc/security-scan' });
} else {
  sgChecks.push({ pass: false, msg: 'No security scan config', file: 'security-scan.json' });
}

sgScore = Math.min(sgScore, 10);

// ============================================================
// 7. COST EFFICIENCY (0-10)
// ============================================================
const costTrackingFiles = glob(AUDIT_ROOT, 'cost-tracking.json');
if (costTrackingFiles.length > 0 || (exists(HOOKS_CONFIG) && readFile(HOOKS_CONFIG).includes('cost'))) {
  costScore += 3;
  costChecks.push({ pass: true, msg: 'Cost tracking configured', file: costTrackingFiles.length > 0 ? path.relative(AUDIT_ROOT, costTrackingFiles[0]) : '~/.claude/hooks.json' });
} else {
  costChecks.push({ pass: false, msg: 'No cost tracking', file: 'cost-tracking.json' });
}

// Token budget skill — check both skill locations
if (exists(path.join(USER_SKILL_DIR, 'token-budget-advisor')) || exists(path.join(AGENT_SKILLS, 'token-budget-advisor'))) {
  costScore += 2;
  costChecks.push({ pass: true, msg: 'token-budget-advisor skill present', file: '~/.claude/skills/ecc/token-budget-advisor/' });
}

// Model routing skill — check both skill locations
if (exists(path.join(USER_SKILL_DIR, 'cost-aware-llm-pipeline')) || exists(path.join(AGENT_SKILLS, 'cost-aware-llm-pipeline'))) {
  costScore += 3;
  costChecks.push({ pass: true, msg: 'cost-aware-llm-pipeline skill present', file: '~/.claude/skills/ecc/cost-aware-llm-pipeline/' });
} else {
  costChecks.push({ pass: false, msg: 'No cost-aware-llm-pipeline skill', file: '~/.claude/skills/ecc/cost-aware-llm-pipeline/' });
}

// Caveman skills (token compression) — check both skill locations
const cavemanSkills = ['caveman', 'caveman-commit', 'caveman-compress', 'caveman-review']
  .filter(s => exists(path.join(USER_SKILL_DIR, s)) || exists(path.join(AGENT_SKILLS, s)));
if (cavemanSkills.length > 0) {
  costScore += Math.min(cavemanSkills.length, 2);
  costChecks.push({ pass: true, msg: `${cavemanSkills.length} caveman skill(s)`, file: '~/.claude/skills/ecc/caveman*/' });
}

costScore = Math.min(costScore, 10);

// ============================================================
// 8. GITHUB INTEGRATION (0-10)
// ============================================================
const ghWorkflowFiles = findWorkflows(AUDIT_ROOT);
ghScore += Math.min(ghWorkflowFiles.length * 2, 4);
ghChecks.push({
  pass: ghWorkflowFiles.length > 0,
  msg: ghWorkflowFiles.length > 0 ? `${ghWorkflowFiles.length} workflow(s)` : 'No GitHub workflows',
  file: ghWorkflowFiles.length > 0 ? path.relative(AUDIT_ROOT, path.dirname(ghWorkflowFiles[0])) : '.github/workflows/',
});

// Issue/PR templates (walk subdirectories for .github/ISSUE_TEMPLATE or PR template)
const ghTemplates = findTemplates(AUDIT_ROOT);
if (ghTemplates.length > 0) {
  ghScore += 2;
  ghChecks.push({ pass: true, msg: 'GitHub templates present', file: path.relative(AUDIT_ROOT, ghTemplates[0]) });
} else {
  ghChecks.push({ pass: false, msg: 'No GitHub issue/PR templates', file: '.github/ISSUE_TEMPLATE/' });
}

// github-ops skill — check both skill locations
if (exists(path.join(USER_SKILL_DIR, 'github-ops')) || exists(path.join(AGENT_SKILLS, 'github-ops'))) {
  ghScore += 2;
  ghChecks.push({ pass: true, msg: 'github-ops skill present', file: '~/.claude/skills/ecc/github-ops/' });
} else {
  ghChecks.push({ pass: false, msg: 'No github-ops skill', file: '~/.claude/skills/ecc/github-ops/' });
}

// Active git hooks
const gitHooksDir = path.join(AUDIT_ROOT, '.git', 'hooks');
const activeHooks = readDir(gitHooksDir).filter(f => !f.endsWith('.sample') && f !== '.gitkeep');
ghScore += activeHooks.length > 0 ? 2 : 0;
ghChecks.push({
  pass: activeHooks.length > 0,
  msg: activeHooks.length > 0 ? `${activeHooks.length} active git hook(s)` : 'No active git hooks',
  file: '.git/hooks/',
});

ghScore = Math.min(ghScore, 10);

// ============================================================
// 9-12. DEPLOY INTEGRATIONS (0-10 each)
// ============================================================
function scoreDeployIntegration(marker, checks) {
  let s = 3; // Has config file
  if (exists(wfDir)) {
    const wfs = readDir(wfDir).filter(f => f.includes('deploy') || f.includes(marker));
    if (wfs.length > 0) {
      s += 3;
      checks.push({ pass: true, msg: `Deploy workflow for ${marker}`, file: path.join(wfDir, wfs[0]) });
    }
  }
  if (hasFile('README.md') && readFile(path.join(AUDIT_ROOT, 'README.md')).toLowerCase().includes(marker)) s += 2;
  if (exists(path.join(USER_SKILL_DIR, `${marker}-deploy`)) || exists(path.join(USER_SKILL_DIR, `crawl-${marker}`))) s += 2;
  return Math.min(s, 10);
}

let vercelScore = 0, netlifyScore = 0, cloudflareScore = 0, flyScore = 0;
if (hasVercel)     vercelScore     = scoreDeployIntegration('vercel', vercelChecks);
if (hasNetlify)    netlifyScore    = scoreDeployIntegration('netlify', netlifyChecks);
if (hasCloudflare) cloudflareScore = scoreDeployIntegration('cloudflare', cloudflareChecks);
if (hasFly)        flyScore        = scoreDeployIntegration('fly', flyChecks);

// ============================================================
// CATEGORY MAP
// ============================================================
const categories = {
  'tool-coverage':        { label: 'Tool Coverage',        score: tcScore,     max: 10, checks: tcChecks },
  'context-efficiency':   { label: 'Context Efficiency',   score: ceScore,     max: 10, checks: ceChecks },
  'quality-gates':        { label: 'Quality Gates',        score: qgScore,     max: 10, checks: qgChecks },
  'memory-persistence':   { label: 'Memory Persistence',   score: mpScore,     max: 10, checks: mpChecks },
  'eval-coverage':        { label: 'Eval Coverage',        score: evScore,     max: 10, checks: evChecks },
  'security-guardrails':  { label: 'Security Guardrails',  score: sgScore,     max: 10, checks: sgChecks },
  'cost-efficiency':      { label: 'Cost Efficiency',      score: costScore,   max: 10, checks: costChecks },
  'github-integration':   { label: 'GitHub Integration',   score: ghScore,     max: 10, checks: ghChecks },
};
if (hasVercel)     categories['vercel-integration']     = { label: 'Vercel Integration',     score: vercelScore,     max: 10, checks: vercelChecks };
if (hasNetlify)    categories['netlify-integration']    = { label: 'Netlify Integration',    score: netlifyScore,    max: 10, checks: netlifyChecks };
if (hasCloudflare) categories['cloudflare-integration'] = { label: 'Cloudflare Integration', score: cloudflareScore, max: 10, checks: cloudflareChecks };
if (hasFly)        categories['fly-integration']         = { label: 'Fly Integration',        score: flyScore,        max: 10, checks: flyChecks };

let totalScore = 0;
let totalMax = 0;
for (const key of applicable) {
  totalScore += categories[key].score;
  totalMax   += categories[key].max;
}

// --- Top actions (prioritized by lowest scores first) ---
const topActions = [];
const entry = (key, label, action) => {
  const c = categories[key];
  if (c && c.score < c.max) topActions.push({ priority: c.score, label, action });
};
entry('github-integration', 'GitHub Integration',
  'Add at least one workflow under .github/workflows/ (.github/workflows/)');
entry('memory-persistence', 'Memory Persistence',
  'Configure hooks.json with post-tool/stop memory hooks (~/.claude/hooks.json)');
entry('security-guardrails', 'Security Guardrails',
  'Add prompt/tool preflight security guards in hooks.json; add security-scan.json (~/.claude/hooks.json)');
entry('eval-coverage', 'Eval Coverage',
  'Write test files and add coverage config (jest/vitest) (*.test.*)');
entry('cost-efficiency', 'Cost Efficiency',
  'Install cost-aware-llm-pipeline skill and configure cost tracking (~/.claude/skills/ecc/)');
entry('quality-gates', 'Quality Gates',
  'Add GitHub CI workflow (.github/workflows/ci.yml)');
entry('context-efficiency', 'Context Efficiency',
  'Expand project context: add CLAUDE.md, .opencode/agents/ context (.)');
entry('tool-coverage', 'Tool Coverage',
  'Enhance tooling: .opencode/ directory, more MCP servers (.)');

topActions.sort((a, b) => a.priority - b.priority);
const finalTopActions = topActions.slice(0, 3);

// --- Suggested ECC skills ---
const suggestedSkills = [];
const suggest = (skillDir, label) => {
  if (!exists(path.join(USER_SKILLS, skillDir)) && !exists(path.join(AGENT_SKILLS, skillDir))) {
    suggestedSkills.push(label);
  }
};
suggest('ecc/cost-aware-llm-pipeline', 'cost-aware-llm-pipeline (model routing → cost savings)');
suggest('ecc/token-budget-advisor', 'token-budget-advisor (response length control)');
suggest('ecc/continuous-learning-v2', 'continuous-learning-v2 (auto-extract instincts from sessions)');
suggest('ecc/e2e-testing', 'e2e-testing (Playwright-based)');
suggest('ecc/security-scan', 'security-scan (AgentShield config audit)');
suggest('ecc/github-ops', 'github-ops (PR/issue CI automation)');
suggest('ecc/blueprint', 'blueprint (multi-session feature planning)');

// --- Scope filter ---
function scopeFilter(catChecks) {
  if (SCOPE === 'repo') return catChecks;
  if (SCOPE === 'hooks')     return catChecks.filter(c => c.file && c.file.includes('hooks'));
  if (SCOPE === 'skills')    return catChecks.filter(c => c.file && c.file.includes('skill'));
  if (SCOPE === 'commands')  return catChecks.filter(c => c.file && c.file.includes('command'));
  if (SCOPE === 'agents')    return catChecks.filter(c => c.file && c.file.includes('agent'));
  return catChecks;
}

// --- Failed checks ---
const failedChecks = [];
for (const key of applicable) {
  for (const c of categories[key].checks) {
    if (!c.pass) failedChecks.push({ category: key, file: c.file, msg: c.msg });
  }
}

// --- Result ---
const result = {
  rubric: '2026-05-19',
  scope: SCOPE,
  root: AUDIT_ROOT,
  detected: { isECC, hasVercel, hasNetlify, hasCloudflare, hasFly },
  overall_score: totalScore,
  max_score: totalMax,
  category_count: applicable.length,
  applicable_categories: applicable,
  categories: {},
  failed_checks: failedChecks,
  top_actions: finalTopActions,
  suggested_ecc_skills: suggestedSkills,
};
for (const key of applicable) {
  const c = categories[key];
  result.categories[key] = {
    label: c.label,
    score: c.score,
    max: c.max,
    checks: scopeFilter(c.checks),
  };
}

// --- Output ---
if (FORMAT === 'json') {
  console.log(JSON.stringify(result, null, 2));
} else {
  const label = isECC ? 'ECC repo' : 'consumer project';
  console.log(`Harness Audit (${SCOPE}, ${label}): ${totalScore}/${totalMax}`);
  console.log('');
  for (const key of applicable) {
    const c = categories[key];
    console.log(`  ${c.label}: ${c.score}/${c.max}`);
  }
  console.log('');
  if (failedChecks.length > 0) {
    console.log('  Failed checks:');
    for (const fc of failedChecks) {
      console.log(`    ✗ [${fc.category}] ${fc.msg} (${fc.file})`);
    }
    console.log('');
  }
  if (finalTopActions.length > 0) {
    console.log('  Top 3 Actions:');
    finalTopActions.forEach((a, i) => {
      console.log(`  ${i+1}) [${a.label}] ${a.action}`);
    });
    console.log('');
  }
  if (suggestedSkills.length > 0) {
    console.log('  Suggested ECC Skills:');
    suggestedSkills.forEach(s => console.log(`    • ${s}`));
  }
}
