/*
 * Interactive page of the benchmark report; HtmlReport.kt embeds it together with the report data.
 *
 * The page has no per-metric logic. Titles, units, descriptions, and the comparison direction come
 * from the `metrics` catalog in the report data, and runs refer to catalog entries by id.
 */
(() => {
    'use strict';

    /**
     * @typedef {Object} Metric  A catalog entry: everything the page knows about one metric.
     * @property {string} title
     * @property {string} unit
     * @property {boolean} higherIsBetter
     * @property {string[]} description  Paragraphs for the chart's info dialog.
     * @property {?('interval'|'rate')} pacing  How a display-paced per-frame metric follows the refresh rate.
     */

    /**
     * @typedef {Object} Configuration  One built and launched combination of target and versions.
     * @property {string} target
     * @property {string} kotlin
     * @property {string} compose
     * @property {?string} browser  Browser executable; null when the target has no browser.
     * @property {?string} browserName
     * @property {Object} metadata  Nested description of the build and target environment.
     */

    /**
     * @typedef {Object} Run  One execution of a test.
     * @property {string} target
     * @property {string} kotlin
     * @property {string} compose
     * @property {?string} browser
     * @property {?string} browserName
     * @property {string} name  Test name.
     * @property {string} type  Measurement mode, one of MODES.
     * @property {number} iteration
     * @property {string} status
     * @property {?string} error
     * @property {Object<string, number>} measurements  Values by metric id.
     * @property {Object<string, number[]>} distributions  Per-frame samples by metric id.
     * @property {Object} parameters
     * @property {?string} reportPath  Raw JSON report, relative to this page.
     */

    /**
     * @typedef {Object} Report
     * @property {string[]} composeVersions  In display order; the first one is the baseline.
     * @property {string[]} kotlinVersions  In display order.
     * @property {Object<string, Metric>} metrics  In display order.
     * @property {Run[]} runs
     * @property {Configuration[]} configurations
     * @property {string[]} warnings
     */

    const byId = id => document.getElementById(id);

    /** @type {Report} */
    const report = JSON.parse(byId('report-data').textContent);
    const { metrics, composeVersions, kotlinVersions } = report;
    /** Everything that says which targets and browsers the session covers. */
    const platformEntries = [...report.configurations, ...report.runs];

    /** Measurement modes, in the order of their tabs. */
    const MODES = ['startup', 'realFrames', 'averageFrame', 'fps'];
    /** Smallest change against the baseline, in percent, that marks a chart as a regression or an improvement. */
    const SIGNIFICANT_CHANGE = 5;
    /** Expected value of a display-paced metric at a given refresh rate, by the metric's pacing. */
    const EXPECTED_VALUE = { interval: fps => 1000 / fps, rate: fps => fps };
    /** Options shared by all charts. */
    const CHART_OPTIONS = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'index', intersect: false },
    };
    const WHISKER_CAP = 6;
    const TOOLTIP_OFFSET = 12;
    const VIEWPORT_MARGIN = 8;

    // ---------- Values ----------

    const unique = values => [...new Set(values)];
    const clamp = (value, min, max) => Math.max(min, Math.min(value, max));
    const formatNumber = (value, maximumFractionDigits = 4) =>
        value.toLocaleString(undefined, { maximumFractionDigits });
    const formatBytes = bytes => `${formatNumber(bytes / 1024 ** 3, 2)} GiB (${bytes.toLocaleString()} bytes)`;

    /** Stable, well-separated series colors: hues step by the golden angle. */
    const seriesColor = index => `hsl(${index * 137.508 % 360}, 68%, 42%)`;
    const kotlinColors = new Map(kotlinVersions.map((kotlin, index) => [kotlin, seriesColor(index)]));

    const runLabel = run => `Kotlin ${run.kotlin} / Compose ${run.compose} / run ${run.iteration}`;
    const hasMeasurements = run => Object.keys(run.measurements).length > 0;
    const hasFailed = run => run.status !== 'passed' || !hasMeasurements(run);

    /** Catalog ids, in catalog order, that occur in at least one of the given id-to-value maps. */
    const metricIdsIn = valueMaps => Object.keys(metrics).filter(id => valueMaps.some(values => id in values));

    /** Mean, extremes, and count of the per-run values behind one chart point; null when there are none. */
    function summarize(values) {
        if (values.length === 0) return null;
        return {
            mean: values.reduce((sum, value) => sum + value / values.length, 0),
            min: Math.min(...values),
            max: Math.max(...values),
            count: values.length,
        };
    }

    /** P0–P100 of the samples, with linear interpolation between sorted values. */
    function percentiles(samples) {
        const sorted = [...samples].sort((a, b) => a - b);
        return Array.from({ length: 101 }, (_, percentile) => {
            const index = (sorted.length - 1) * percentile / 100;
            const low = sorted[Math.floor(index)];
            const high = sorted[Math.ceil(index)];
            return low + (high - low) * (index - Math.floor(index));
        });
    }

    /** Change of `value` against `baseline`, in percent; null without a usable baseline. */
    function changePercent(value, baseline) {
        if (!Number.isFinite(value) || !Number.isFinite(baseline) || baseline === 0) return null;
        const change = (value - baseline) / Math.abs(baseline) * 100;
        return Number.isFinite(change) ? change : null;
    }

    const isImprovement = (metric, change) => metric.higherIsBetter ? change > 0 : change < 0;

    // ---------- DOM ----------

    /**
     * Creates an element. `props` are DOM properties such as `className`, `textContent`, or `onclick`;
     * `attrs` are attributes set as is, such as `role` and `aria-*`.
     */
    function el(tag, { attrs = {}, ...props } = {}, children = []) {
        const node = Object.assign(document.createElement(tag), props);
        for (const [name, value] of Object.entries(attrs)) node.setAttribute(name, value);
        node.append(...children);
        return node;
    }

    /** Makes `button` show and hide `panel`. */
    function toggleOnClick(button, panel) {
        button.addEventListener('click', () => {
            panel.hidden = !panel.hidden;
            button.setAttribute('aria-expanded', String(!panel.hidden));
        });
    }

    /** A collapsed list of problems. */
    const warningList = (summary, items) => el('details', { className: 'warning' }, [
        el('summary', { textContent: summary }),
        el('ul', {}, items.map(item => el('li', { textContent: item }))),
    ]);

    /** A table that scrolls horizontally inside a focusable region. */
    const scrollableTable = (label, tableClass, columnHeaders, body) => el('div', {
        className: 'environment-scroll',
        tabIndex: 0,
        attrs: { role: 'region', 'aria-label': label },
    }, [
        el('table', { className: tableClass }, [el('thead', {}, [el('tr', {}, columnHeaders)]), body]),
    ]);

    // ---------- Dialog ----------

    const dialog = byId('dialog');
    byId('dialog-close').addEventListener('click', () => dialog.close());
    // A click on the backdrop targets the dialog itself but lands outside its box.
    dialog.addEventListener('click', event => {
        const { left, right, top, bottom } = dialog.getBoundingClientRect();
        const inside = event.clientX >= left && event.clientX <= right
            && event.clientY >= top && event.clientY <= bottom;
        if (event.target === dialog && !inside) dialog.close();
    });

    function showDialog(title, content) {
        byId('dialog-title').textContent = title;
        byId('dialog-content').replaceChildren(...content);
        dialog.showModal();
    }

    const dialogButton = (text, label, onclick) => el('button', {
        type: 'button',
        className: 'chart-raw-button',
        textContent: text,
        onclick,
        attrs: { 'aria-label': label, 'aria-haspopup': 'dialog', 'aria-controls': dialog.id },
    });

    // ---------- Selection: target, browser, mode, test ----------

    /** A radio group shown as a segmented control. */
    class SegmentedControl {
        #root;
        #onChange;
        /** The checked option; null when there are no options. */
        value = null;

        constructor(root, onChange) {
            this.#root = root;
            this.#onChange = onChange;
        }

        /** Replaces the options, keeping the current value when it is still among them. */
        setOptions(values, labelOf = value => value) {
            this.value = values.includes(this.value) ? this.value : values[0] ?? null;
            this.#root.replaceChildren(...values.map(value => el('label', {}, [
                el('input', {
                    type: 'radio',
                    name: this.#root.id,
                    value,
                    checked: value === this.value,
                    onchange: () => {
                        this.value = value;
                        this.#onChange();
                    },
                }),
                el('span', { textContent: labelOf(value) }),
            ])));
        }
    }

    // Each control narrows the choices of the ones after it, so a change refreshes everything downstream.
    const target = new SegmentedControl(byId('target'), refreshBrowsers);
    const browser = new SegmentedControl(byId('browser'), refreshModes);
    const mode = new SegmentedControl(byId('mode'), refreshTests);
    const test = new SegmentedControl(byId('test'), renderCharts);

    /** Whether a run or configuration belongs to the selected target and, if the target has any, browser. */
    const onSelectedPlatform = entry => entry.target === target.value
        && (browser.value === null || entry.browser === browser.value);

    function refreshBrowsers() {
        const entries = platformEntries.filter(entry => entry.target === target.value);
        browser.setOptions(
            unique(entries.map(entry => entry.browser).filter(Boolean)),
            value => entries.find(entry => entry.browser === value).browserName,
        );
        byId('browser-control').hidden = browser.value === null;
        refreshModes();
    }

    function refreshModes() {
        renderEnvironment();
        const runs = report.runs.filter(run => onSelectedPlatform(run) && hasMeasurements(run));
        mode.setOptions(MODES.filter(type => runs.some(run => run.type === type)));
        refreshTests();
    }

    function refreshTests() {
        const runs = report.runs.filter(run => onSelectedPlatform(run) && run.type === mode.value);
        test.setOptions(unique(runs.map(run => run.name)));
        renderCharts();
    }

    // ---------- Environment ----------

    const ENVIRONMENT_LABELS = {
        configuration: 'Configuration', launcher: 'Build and launch', runtime: 'Target process',
        target: 'Target', kotlinVersion: 'Kotlin', composeVersion: 'Compose', localCompose: 'Local Compose',
        browser: 'Browser', kind: 'Family', executable: 'Executable',
        capturedAt: 'Launcher snapshot (UTC)', buildProfile: 'Build profile', optimization: 'Optimization',
        coreCommit: 'Core commit', coreDirty: 'Core has uncommitted changes', browserVersion: 'Browser version',
        iosTarget: 'iOS target', type: 'Type', simulator: 'Simulator', host: 'Host', xcodeVersion: 'Xcode', sdkVersion: 'SDK version',
        osName: 'OS', osVersion: 'OS version',
        architecture: 'Process architecture', cpuModel: 'CPU', availableProcessors: 'Available logical processors',
        physicalMemoryBytes: 'Physical memory (process-reported)', id: 'ID', name: 'Name', state: 'Initial state',
        deviceTypeIdentifier: 'Device type', runtimeIdentifier: 'Runtime identifier', runtimeVersion: 'Runtime version',
        runtimeBuild: 'Runtime build', schemaVersion: 'Schema version', deviceModel: 'Hardware model',
        simulatorModel: 'Simulated device model', gpuName: 'GPU / renderer', graphicsBackend: 'Graphics backend',
        jvmName: 'JVM', jvmVersion: 'JVM version',
        jvmVendor: 'JVM vendor', javaVersion: 'Java version', jvmArguments: 'JVM arguments', maxHeapBytes: 'Maximum JVM heap',
        userAgent: 'User agent', browserPlatform: 'Browser platform', deviceMemoryGiB: 'Approximate browser memory (GiB)',
        screenWidth: 'Screen width (CSS px)', screenHeight: 'Screen height (CSS px)', devicePixelRatio: 'Device pixel ratio',
        warnings: 'Warnings',
    };
    /** Fields that are shown differently from plain text. */
    const ENVIRONMENT_FIELDS = {
        physicalMemoryBytes: { format: formatBytes },
        maxHeapBytes: { format: formatBytes },
        warnings: { highlighted: true },
    };
    const environmentLabel = key => ENVIRONMENT_LABELS[key] ?? key;

    /** Yields the non-empty leaf values of nested metadata with the keys leading to them. */
    function* metadataLeaves(value, path = []) {
        if (value == null || (Array.isArray(value) && value.length === 0)) return;
        if (typeof value === 'object' && !Array.isArray(value)) {
            for (const [key, child] of Object.entries(value)) yield* metadataLeaves(child, [...path, key]);
        } else {
            yield { path, value };
        }
    }

    function environmentText(field, value) {
        if (Array.isArray(value)) return value.join('\n');
        if (typeof value === 'boolean') return value ? 'Yes' : 'No';
        return ENVIRONMENT_FIELDS[field]?.format?.(value) ?? String(value);
    }

    /** Shows the environments of the selected platform's configurations side by side. */
    function renderEnvironment() {
        const container = byId('environment-info');
        const configurations = report.configurations.filter(onSelectedPlatform);
        if (configurations.length === 0) {
            container.replaceChildren(el('p', {
                className: 'environment-empty',
                textContent: 'Environment metadata is unavailable in this report.',
            }));
            return;
        }

        // One row per leaf of any configuration's metadata, with one cell per configuration.
        const rowsByPath = new Map();
        configurations.forEach((configuration, column) => {
            for (const { path, value } of metadataLeaves(configuration.metadata)) {
                const key = path.join('.');
                if (!rowsByPath.has(key)) rowsByPath.set(key, { path, cells: configurations.map(() => '—') });
                rowsByPath.get(key).cells[column] = environmentText(path.at(-1), value);
            }
        });
        const rows = [...rowsByPath.values()];

        const tableRow = ({ path: [group, ...fields], cells }) => {
            const highlighted = ENVIRONMENT_FIELDS[fields.at(-1)]?.highlighted;
            return el('tr', highlighted ? { className: 'environment-warning' } : {}, [
                el('th', {
                    scope: 'row',
                    textContent: fields.length > 0 ? fields.map(environmentLabel).join(' / ') : environmentLabel(group),
                }),
                // Configurations that agree share one cell.
                ...(cells.every(cell => cell === cells[0])
                    ? [el('td', { colSpan: configurations.length, textContent: cells[0] })]
                    : cells.map(cell => el('td', { textContent: cell }))),
            ]);
        };
        const groups = unique(rows.map(row => row.path[0]));

        container.replaceChildren(
            scrollableTable('Environment comparison table', 'environment-table', [
                el('th', { scope: 'col', textContent: 'Parameter' }),
                ...configurations.map(configuration => el('th', { scope: 'col' }, [
                    el('span', { textContent: `Kotlin ${configuration.kotlin}` }),
                    el('small', { textContent: `Compose ${configuration.compose}` }),
                ])),
            ], el('tbody', {}, groups.flatMap(group => [
                el('tr', { className: 'environment-group' }, [
                    el('th', { colSpan: configurations.length + 1, textContent: environmentLabel(group) }),
                ]),
                ...rows.filter(row => row.path[0] === group).map(tableRow),
            ]))),
            el('p', {
                className: 'environment-note',
                textContent: 'Memory and CPU values describe what the process can see. Browser memory is approximate; '
                    + 'simulator hardware may describe its host. JVM heap is not physical RAM. '
                    + 'JVM GPU names list detected adapters, not necessarily the active rendering device.',
            }),
        );
    }

    // ---------- Test ----------

    const PARAMETER_ORDER = ['type', 'width', 'height', 'targetFps', 'warmupCount', 'frameCount', 'repeat', 'timeoutSeconds'];

    function parameterText(value) {
        if (value == null) return '—';
        return typeof value === 'object' ? JSON.stringify(value) : String(value);
    }

    /** The test's heading and a collapsed table of the parameters its runs were measured with. */
    function testInfo(name, runs) {
        const parameters = Object.assign({}, ...runs.map(run => run.parameters));
        const keys = Object.keys(parameters);
        const orderedKeys = [
            ...PARAMETER_ORDER.filter(key => keys.includes(key)),
            ...keys.filter(key => !PARAMETER_ORDER.includes(key)),
        ];
        const panel = el('div', { className: 'test-parameters', id: 'test-parameters', hidden: true }, [
            scrollableTable(`${name} parameters`, 'environment-table test-parameters-table', [
                el('th', { scope: 'col', textContent: 'Parameter' }),
                el('th', { scope: 'col', textContent: 'Value' }),
            ], el('tbody', {}, orderedKeys.map(key => el('tr', {}, [
                el('th', { scope: 'row', textContent: key }),
                el('td', { textContent: parameterText(parameters[key]) }),
            ])))),
        ]);
        const button = el('button', {
            type: 'button',
            className: 'test-info-button',
            textContent: 'info',
            attrs: { 'aria-label': `Parameters for ${name}`, 'aria-expanded': 'false', 'aria-controls': panel.id },
        });
        toggleOnClick(button, panel);
        return [el('div', { className: 'test-heading' }, [el('h2', { textContent: name }), button]), panel];
    }

    // ---------- Value tooltip of trend charts ----------

    const valueTooltip = byId('value-tooltip');
    const hideValueTooltip = () => { valueTooltip.hidden = true; };
    window.addEventListener('scroll', hideValueTooltip, true);
    window.addEventListener('resize', hideValueTooltip);

    /** How a point's mean compares with its series' baseline. */
    function baselineChange(metric, mean, baseline, baselineLabel) {
        const change = changePercent(mean, baseline);
        if (change === null) {
            return el('div', { textContent: `Change ${baselineLabel}: unavailable (baseline missing or zero)` });
        }
        const percent = change.toLocaleString(undefined, { maximumFractionDigits: 2, signDisplay: 'exceptZero' });
        if (change === 0) return el('div', { textContent: `${percent}% ${baselineLabel}` });
        return isImprovement(metric, change)
            ? el('div', { className: 'baseline-better', textContent: `${percent}% ${baselineLabel} — better` })
            : el('div', { className: 'baseline-worse', textContent: `${percent}% ${baselineLabel} — worse` });
    }

    /** Chart.js external tooltip: what is behind the hovered points of a trend chart. */
    function showValueTooltip({ chart, tooltip }, metric) {
        if (!tooltip.opacity || !tooltip.dataPoints?.length) {
            hideValueTooltip();
            return;
        }
        const baselineLabel = `vs ${chart.data.labels[0]} baseline`;
        const series = tooltip.dataPoints
            .map(({ dataset, dataIndex }) => ({ dataset, range: dataset.ranges[dataIndex] }))
            .filter(({ range }) => range !== null);
        valueTooltip.replaceChildren(
            el('strong', { textContent: tooltip.title.join(' — ') }),
            ...series.map(({ dataset, range }) => el('div', { className: 'value-tooltip-series' }, [
                el('div', { textContent: `${dataset.label}: mean ${formatNumber(range.mean)} ${metric.unit}` }),
                el('div', { textContent: `Min–max: ${formatNumber(range.min)}–${formatNumber(range.max)} ${metric.unit}` }),
                el('div', { textContent: `Runs: ${range.count}` }),
                baselineChange(metric, range.mean, dataset.baseline, baselineLabel),
            ])),
        );
        valueTooltip.hidden = false;
        // Next to the pointer, but inside the viewport. Moving the tooltip sideways can rewrap its text,
        // so its height is known only after that.
        const bounds = chart.canvas.getBoundingClientRect();
        const left = clamp(bounds.left + tooltip.caretX + TOOLTIP_OFFSET,
            VIEWPORT_MARGIN, window.innerWidth - valueTooltip.offsetWidth - VIEWPORT_MARGIN);
        valueTooltip.style.left = `${left}px`;
        const top = clamp(bounds.top + tooltip.caretY + TOOLTIP_OFFSET,
            VIEWPORT_MARGIN, window.innerHeight - valueTooltip.offsetHeight - VIEWPORT_MARGIN);
        valueTooltip.style.top = `${top}px`;
    }

    // ---------- Chart.js helpers and the plugins of trend charts ----------

    /** Yields the datasets that are currently shown, with their point elements. */
    function* visibleDatasets(chart) {
        for (const [index, dataset] of chart.data.datasets.entries()) {
            if (chart.isDatasetVisible(index)) yield { dataset, points: chart.getDatasetMeta(index).data };
        }
    }

    /** Calls `draw` with the canvas clipped to the chart area. */
    function clipToChartArea({ ctx, chartArea }, draw) {
        ctx.save();
        ctx.beginPath();
        ctx.rect(chartArea.left, chartArea.top, chartArea.width, chartArea.height);
        ctx.clip();
        draw(ctx);
        ctx.restore();
    }

    /** Draws the minimum–maximum of the per-run values around each mean. */
    const minMaxWhiskers = {
        id: 'minMaxWhiskers',
        // Include the extremes, not just the mean, when Chart.js chooses axis limits.
        afterDataLimits(chart, { scale }) {
            if (scale.axis !== 'y') return;
            for (const { dataset } of visibleDatasets(chart)) {
                for (const range of dataset.ranges) {
                    if (range === null) continue;
                    scale.min = Math.min(scale.min, range.min);
                    scale.max = Math.max(scale.max, range.max);
                }
            }
        },
        beforeDatasetsDraw(chart) {
            const { y } = chart.scales;
            clipToChartArea(chart, ctx => {
                ctx.lineWidth = 2;
                for (const { dataset, points } of visibleDatasets(chart)) {
                    ctx.strokeStyle = dataset.borderColor;
                    dataset.ranges.forEach((range, index) => {
                        if (range === null || points[index].skip) return;
                        const { x } = points[index];
                        const top = y.getPixelForValue(range.max);
                        const bottom = y.getPixelForValue(range.min);
                        ctx.beginPath();
                        ctx.moveTo(x, top);
                        ctx.lineTo(x, bottom);
                        ctx.moveTo(x - WHISKER_CAP, top);
                        ctx.lineTo(x + WHISKER_CAP, top);
                        ctx.moveTo(x - WHISKER_CAP, bottom);
                        ctx.lineTo(x + WHISKER_CAP, bottom);
                        ctx.stroke();
                    });
                }
            });
        },
    };

    /** Draws each series' baseline, its mean at the first Compose version, across the chart. */
    const baselineLines = {
        id: 'baselineLines',
        beforeDatasetsDraw(chart) {
            const { chartArea, scales: { y } } = chart;
            clipToChartArea(chart, ctx => {
                ctx.strokeStyle = '#2563eb';
                ctx.lineWidth = 1;
                for (const { dataset } of visibleDatasets(chart)) {
                    if (dataset.baseline === null) continue;
                    const baselineY = y.getPixelForValue(dataset.baseline);
                    ctx.beginPath();
                    ctx.moveTo(chartArea.left, baselineY);
                    ctx.lineTo(chartArea.right, baselineY);
                    ctx.stroke();
                }
            });
        },
    };

    /** Marks the card when a shown series ends noticeably better or worse than its baseline. */
    function highlightChange(card, chart, metric) {
        const changes = [...visibleDatasets(chart)]
            .map(({ dataset }) => changePercent(dataset.ranges.at(-1)?.mean, dataset.baseline))
            .filter(change => change !== null && Math.abs(change) >= SIGNIFICANT_CHANGE);
        const regression = changes.some(change => !isImprovement(metric, change));
        const improvement = changes.some(change => isImprovement(metric, change));
        card.classList.toggle('chart-regression', regression);
        card.classList.toggle('chart-improvement', !regression && improvement);
        const status = regression ? `regression of at least ${SIGNIFICANT_CHANGE}%`
            : improvement ? `improvement of at least ${SIGNIFICANT_CHANGE}%`
            : `no change of at least ${SIGNIFICANT_CHANGE}% with a valid baseline`;
        card.setAttribute('aria-label',
            `${metric.title}: ${status}, comparing the last Compose version with the first`);
    }

    // ---------- Charts ----------

    const TREND_READING = 'X: Compose version. Y: metric value. Each line is a Kotlin version; points average the per-run values, '
        + 'and whiskers show the minimum–maximum across repeated runs, not across individual frames. '
        + 'Thin blue lines mark each Kotlin series’ baseline at the first Compose version. '
        + 'Tooltip changes compare point means with that baseline; green is better, red is worse. '
        + 'Percentage changes are unavailable when the baseline is missing or zero. '
        + `A red border means at least one visible Kotlin series regressed by ${SIGNIFICANT_CHANGE}% or more at the last Compose version. `
        + `Otherwise, a green border means at least one improved by ${SIGNIFICANT_CHANGE}% or more.`;

    const expectedFpsInput = byId('expected-fps');
    /** Refresh rate that the scales of display-paced distribution charts are built around. */
    let expectedFps = expectedFpsInput.valueAsNumber;
    expectedFpsInput.addEventListener('change', () => {
        const value = expectedFpsInput.valueAsNumber;
        if (!expectedFpsInput.reportValidity() || !Number.isFinite(value)) return;
        expectedFps = value;
        renderCharts();
    });

    /** Kotlin versions unchecked in the filter. */
    const hiddenKotlin = new Set();
    /** Charts on the page. */
    let charts = [];
    /** Cards of the distribution charts, each with the only Kotlin version it shows. */
    let distributionCards = [];

    /** Paragraphs of a chart's info dialog: what the metric is, then how to read the chart. */
    const explanation = (metric, reading) =>
        [metric.higherIsBetter ? 'Higher is better.' : 'Lower is better.', ...metric.description, reading];

    function distributionReading(metric) {
        const scale = metric.pacing
            ? `Y starts at zero; its upper bound is twice the expected value (${expectedFps} FPS or ${1000 / expectedFps} ms), `
                + 'expanded if needed to include outliers. '
                + 'Expected FPS only controls the chart scale, not the benchmark or display refresh rate.'
            : 'Non-positive percentile values are omitted.';
        return `X: percentile P0–P100 (minimum to maximum). Y: metric value on a ${metric.pacing ? 'linear' : 'logarithmic'} scale. `
            + 'Frame samples from repeated runs are pooled per Compose version and Kotlin version. '
            + `Percentiles use linear interpolation. ${scale}`;
    }

    /** Content of the dialog that links to the raw JSON reports behind a chart. */
    function rawReports(chartTitle, runs) {
        const links = runs.filter(run => run.reportPath).map(run => {
            const segments = run.reportPath.split('/');
            return el('li', {}, [
                el('small', { textContent: runLabel(run) }),
                el('a', {
                    textContent: segments.at(-1),
                    href: `./${segments.map(encodeURIComponent).join('/')}`,
                    target: '_blank',
                    rel: 'noopener',
                }),
            ]);
        });
        return [
            el('p', { textContent: `${test.value} — ${chartTitle}` }),
            links.length > 0
                ? el('ul', { className: 'raw-report-list' }, links)
                : el('p', { textContent: 'No raw reports for the currently visible series.' }),
        ];
    }

    /**
     * A card with a heading above an empty canvas. `explain` returns the paragraphs of the info dialog;
     * `shownRuns` returns the runs behind the series that the chart drawn on the canvas currently shows.
     */
    function chartCard({ title, canvasLabel, explain, shownRuns }) {
        const canvas = el('canvas', { attrs: { role: 'img', 'aria-label': canvasLabel } });
        const card = el('article', { className: 'chart-card' }, [
            el('div', { className: 'chart-heading' }, [
                el('h3', { textContent: title }),
                el('div', { className: 'chart-actions' }, [
                    dialogButton('ⓘ', `About ${title}`,
                        () => showDialog(title, explain().map(text => el('p', { textContent: text })))),
                    dialogButton('raw', `Raw JSON reports for ${title}`,
                        () => showDialog('Raw reports', rawReports(title, shownRuns(Chart.getChart(canvas))))),
                ]),
            ]),
            el('div', { className: 'canvas-container' }, [canvas]),
        ]);
        return { card, canvas };
    }

    /** How one metric changes across Compose versions, with a line per Kotlin version. */
    function trendChart(id, runs) {
        const metric = metrics[id];
        const { card, canvas } = chartCard({
            title: `${metric.title} (${metric.unit})`,
            canvasLabel: `${test.value}: ${metric.title}, ${metric.unit}, by Compose version`,
            explain: () => explanation(metric, TREND_READING),
            shownRuns: chart => {
                const shown = new Set([...visibleDatasets(chart)].map(({ dataset }) => dataset.kotlin));
                return runs.filter(run => shown.has(run.kotlin) && id in run.measurements);
            },
        });
        const datasets = kotlinVersions
            .filter(kotlin => runs.some(run => run.kotlin === kotlin && id in run.measurements))
            .map(kotlin => {
                const ranges = composeVersions.map(compose => summarize(runs
                    .filter(run => run.kotlin === kotlin && run.compose === compose)
                    .map(run => run.measurements[id])
                    .filter(Number.isFinite)));
                const color = kotlinColors.get(kotlin);
                return {
                    kotlin,
                    label: `Kotlin ${kotlin}`,
                    data: ranges.map(range => range?.mean ?? null),
                    ranges,
                    baseline: ranges[0]?.mean ?? null,
                    borderColor: color,
                    backgroundColor: color,
                    borderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    hidden: hiddenKotlin.has(kotlin),
                    spanGaps: false,
                };
            });
        return {
            card,
            canvas,
            config: {
                type: 'line',
                data: { labels: composeVersions, datasets },
                plugins: [baselineLines, minMaxWhiskers, {
                    id: 'baselineHighlight',
                    afterUpdate: chart => highlightChange(card, chart, metric),
                }],
                options: {
                    ...CHART_OPTIONS,
                    scales: {
                        x: { offset: true, title: { display: true, text: 'Compose version' } },
                        y: { beginAtZero: true, title: { display: true, text: metric.unit } },
                    },
                    plugins: {
                        tooltip: { enabled: false, external: context => showValueTooltip(context, metric) },
                        legend: { display: false },
                    },
                },
            },
        };
    }

    /** Percentile curves of one Kotlin version's per-frame samples, with a line per Compose version. */
    function distributionChart(id, kotlin, runs) {
        const metric = metrics[id];
        const logarithmic = !metric.pacing;
        const series = composeVersions.flatMap((compose, index) => {
            const samples = runs.filter(run => run.compose === compose).flatMap(run => run.distributions[id] ?? []);
            if (samples.length === 0) return [];
            return [{ compose, color: seriesColor(index), count: samples.length, curve: percentiles(samples) }];
        });
        if (series.length === 0) return null;

        const { card, canvas } = chartCard({
            title: `${metric.title} (${metric.unit}) — Kotlin ${kotlin}`,
            canvasLabel: `${test.value}: ${metric.title} percentiles, Kotlin ${kotlin}, by Compose version`,
            explain: () => explanation(metric, distributionReading(metric)),
            shownRuns: chart => {
                // Compose versions can be switched off in the chart's legend.
                const shown = new Set([...visibleDatasets(chart)].map(({ dataset }) => dataset.compose));
                return runs.filter(run => shown.has(run.compose) && run.distributions[id]?.length > 0);
            },
        });
        card.hidden = hiddenKotlin.has(kotlin);
        // Display-paced metrics get a linear scale from zero to twice the expected value, stretched to
        // fit outliers. P100, the last point of a curve, is its maximum.
        const yScale = logarithmic ? { type: 'logarithmic' } : {
            type: 'linear',
            min: 0,
            max: Math.max(2 * EXPECTED_VALUE[metric.pacing](expectedFps), ...series.map(({ curve }) => curve.at(-1))),
        };
        return {
            card,
            canvas,
            kotlin,
            config: {
                type: 'line',
                data: {
                    datasets: series.map(({ compose, color, count, curve }) => ({
                        kotlin,
                        compose,
                        label: `Compose ${compose} (n=${count})`,
                        // A logarithmic scale cannot show non-positive values.
                        data: curve.map((value, percentile) =>
                            ({ x: percentile, y: logarithmic && value <= 0 ? null : value })),
                        borderColor: color,
                        backgroundColor: color,
                        borderWidth: 2,
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        tension: 0,
                        hidden: hiddenKotlin.has(kotlin),
                    })),
                },
                options: {
                    ...CHART_OPTIONS,
                    scales: {
                        x: {
                            type: 'linear',
                            min: 0,
                            max: 100,
                            title: { display: true, text: 'Percentile' },
                            ticks: { stepSize: 10 },
                        },
                        y: { ...yScale, title: { display: true, text: `${metric.title} (${metric.unit})` } },
                    },
                    plugins: {
                        tooltip: {
                            callbacks: {
                                title: items => items.length > 0 ? `P${items[0].parsed.x}` : '',
                                label: ({ dataset, parsed }) =>
                                    `${dataset.label}: ${formatNumber(parsed.y)} ${metric.unit}`,
                            },
                        },
                    },
                },
            },
        };
    }

    /** Shows the selected test: its parameters, failed runs, and charts. */
    function renderCharts() {
        hideValueTooltip();
        charts.forEach(chart => chart.destroy());
        charts = [];
        distributionCards = [];
        byId('charts').replaceChildren();
        const expectedFpsControl = byId('expected-fps-control');
        expectedFpsControl.hidden = true;
        const message = byId('message');
        if (test.value === null) {
            message.textContent = 'No measurements were collected in this session. See summary.json and logs for errors.';
            return;
        }
        if (!window.Chart) {
            message.textContent = 'The embedded Chart.js library could not be loaded. Regenerate this report and reload the file.';
            return;
        }
        message.textContent = '';

        const runs = report.runs.filter(run =>
            onSelectedPlatform(run) && run.type === mode.value && run.name === test.value);
        const failures = runs.filter(hasFailed);
        const trendIds = metricIdsIn(runs.map(run => run.measurements));
        const distributionIds = metricIdsIn(runs.map(run => run.distributions));
        expectedFpsControl.hidden = !distributionIds.some(id => metrics[id].pacing);

        const trends = trendIds.map(id => trendChart(id, runs));
        // One grid of distribution charts per Kotlin version that has per-frame samples.
        const distributions = kotlinVersions
            .map(kotlin => {
                const kotlinRuns = runs.filter(run => run.kotlin === kotlin);
                return distributionIds.map(id => distributionChart(id, kotlin, kotlinRuns)).filter(chart => chart !== null);
            })
            .filter(grid => grid.length > 0);
        const chartGrid = cards => el('div', { className: 'chart-grid' }, cards.map(({ card }) => card));

        byId('charts').append(el('section', {}, [
            ...testInfo(test.value, runs),
            ...(failures.length > 0 ? [warningList(
                `${failures.length} runs failed or have no measurements`,
                failures.map(run => `${runLabel(run)}: ${run.error || run.status}`),
            )] : []),
            ...(trendIds.length > 0 ? [] : [el('p', { textContent: 'No measurements available for this test.' })]),
            chartGrid(trends),
            ...(distributionIds.length > 0 ? [el('h2', { textContent: 'Per-frame percentiles' })] : []),
            ...distributions.map(chartGrid),
        ]));
        // Charts take the size of their containers, so create them once the whole page is laid out.
        charts = [...trends, ...distributions.flat()].map(({ canvas, config }) => new Chart(canvas, config));
        distributionCards = distributions.flat().map(({ card, kotlin }) => ({ card, kotlin }));
    }

    // ---------- Kotlin filter ----------

    function applyKotlinFilter() {
        hideValueTooltip();
        for (const { card, kotlin } of distributionCards) card.hidden = hiddenKotlin.has(kotlin);
        for (const chart of charts) {
            chart.data.datasets.forEach((dataset, index) => {
                chart.setDatasetVisibility(index, !hiddenKotlin.has(dataset.kotlin));
            });
            chart.resize();
            chart.update();
        }
    }

    byId('kotlin-versions').append(...kotlinVersions.map(kotlin => el('label', { className: 'kotlin-option' }, [
        el('input', {
            type: 'checkbox',
            value: kotlin,
            checked: true,
            onchange: ({ target: checkbox }) => {
                if (checkbox.checked) hiddenKotlin.delete(kotlin); else hiddenKotlin.add(kotlin);
                applyKotlinFilter();
            },
        }),
        el('span', {
            className: 'color-swatch',
            style: `background-color: ${kotlinColors.get(kotlin)}`,
            attrs: { 'aria-hidden': 'true' },
        }),
        el('span', { textContent: kotlin }),
    ])));

    // ---------- Start ----------

    toggleOnClick(byId('environment-info-button'), byId('environment-info'));
    if (report.warnings.length > 0) {
        byId('warnings').append(warningList(`${report.warnings.length} report warnings`, report.warnings));
    }
    target.setOptions(unique(platformEntries.map(entry => entry.target)));
    refreshBrowsers();
})();
