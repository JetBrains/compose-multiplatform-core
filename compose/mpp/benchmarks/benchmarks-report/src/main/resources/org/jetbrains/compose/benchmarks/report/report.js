(() => {
    'use strict';

    const report = JSON.parse(document.getElementById('report-data').textContent);
    const target = document.getElementById('target');
    const browser = document.getElementById('browser');
    const browserControl = document.getElementById('browser-control');
    const mode = document.getElementById('mode');
    const test = document.getElementById('test');
    const charts = document.getElementById('charts');
    const message = document.getElementById('message');
    const environmentInfoButton = document.getElementById('environment-info-button');
    const environmentInfo = document.getElementById('environment-info');
    environmentInfoButton.addEventListener('click', () => {
        environmentInfo.hidden = !environmentInfo.hidden;
        environmentInfoButton.setAttribute('aria-expanded', String(!environmentInfo.hidden));
    });
    const modes = ['startup', 'realFrames', 'averageFrame', 'fps'];
    const distinct = values => [...new Set(values)];
    const configurations = report.configurations ?? [];
    const kotlinVersions = distinct([...report.runs, ...configurations].map(run => run.kotlin));
    const hidden = new Set();
    let instances = [];
    let distributionCards = [];

    function element(tag, text, parent, className) {
        const node = document.createElement(tag);
        if (text != null) node.textContent = text;
        if (className) node.className = className;
        parent.appendChild(node);
        return node;
    }

    const rawDialog = element('dialog', null, document.body, 'raw-dialog');
    rawDialog.setAttribute('aria-labelledby', 'raw-dialog-title');
    rawDialog.addEventListener('click', event => {
        const bounds = rawDialog.getBoundingClientRect();
        if (event.target === rawDialog && (event.clientX < bounds.left || event.clientX > bounds.right
            || event.clientY < bounds.top || event.clientY > bounds.bottom)) rawDialog.close();
    });

    const infoDialog = element('dialog', null, document.body, 'raw-dialog');
    infoDialog.id = 'chart-info-dialog';
    infoDialog.setAttribute('aria-labelledby', 'chart-info-title');
    infoDialog.addEventListener('click', event => {
        const bounds = infoDialog.getBoundingClientRect();
        if (event.target === infoDialog && (event.clientX < bounds.left || event.clientX > bounds.right
            || event.clientY < bounds.top || event.clientY > bounds.bottom)) infoDialog.close();
    });

    function higherIsBetter(metric) {
        return ['Throughput', 'Frame rate', 'Frame FPS', 'Average frame FPS'].includes(metric.split(' — ')[0]);
    }

    function chartExplanation(metric, distribution) {
        const [name, statistic] = metric.split(' — ');
        const descriptions = {
            'Process to main': 'Elapsed time from process launch to main entry, when available.',
            'Main to first frame': 'Elapsed time from main entry to the first observed frame callback.',
            'Total duration': 'Wall-clock time for the measured frame-loop intervals. Compare runs with the same frame count.',
            'Throughput': 'Measured frame count divided by total wall-clock duration, in FPS.',
            'Frame time': 'Wall-clock intervals between frame-loop callbacks, including synchronization waits and scheduling delays; not CPU render time or presentation timestamps.',
            'Frame rate': '1000 / frame interval in milliseconds, using only positive, finite frame-loop intervals; not measured display FPS.',
            'Over-budget intervals': 'Percentage of positive, finite frame-loop intervals longer than 17.5 ms: a fixed 60 Hz budget (1000/60 ms) plus 5% tolerance.',
            'Estimated missed VSync': 'Estimated missed slots / total slots × 100, using positive, finite frame-loop intervals. Slots = max(1, ceil(interval / (1000/60 ms) − 0.05)). Assumes 60 Hz with 5% tolerance, not measured presentation events; scheduling jitter can inflate this estimate.',
            'CPU time': 'Per-frame offscreen elapsed time minus the GPU-completion wait. Includes CPU work and other elapsed overhead, not CPU utilization.',
            'GPU time': 'Per-frame wall-clock time waiting for GPU completion during offscreen rendering; not GPU timestamp timing.',
            'Frame FPS': 'Reciprocal of per-frame offscreen elapsed time, including the GPU-completion wait; not display FPS.',
            'Average CPU time': 'Total offscreen measurement time minus GPU-completion waits, divided by frame count.',
            'Average GPU time': 'Total offscreen GPU-completion wait time divided by frame count; not GPU timestamp timing.',
            'Average frame FPS': 'Offscreen frame count divided by total measurement time, including GPU-completion waits; not display FPS.',
        };
        const description = name.startsWith('Main to Nth frame')
            ? 'Elapsed time from main entry to the configured Nth frame callback after the first frame.'
            : descriptions[name];
        const paragraphs = [higherIsBetter(metric) ? 'Higher is better.' : 'Lower is better.', description];
        if (statistic) {
            paragraphs.push(statistic === 'mean' ? 'Uses the arithmetic mean of finite frame samples in each run.'
                : `Uses ${statistic.toUpperCase()} of finite frame samples in each run, with linear interpolation between sorted values.`);
        }
        if (distribution) {
            paragraphs.push('X: percentile P0–P100 (minimum to maximum). Y: metric value on a logarithmic scale. '
                + 'Frame samples from repeated runs are pooled per Compose version and Kotlin version. '
                + 'Percentiles use linear interpolation; non-positive percentile values are omitted.');
        } else {
            paragraphs.push('X: Compose version. Y: metric value. Each line is a Kotlin version; points average the per-run values, '
                + 'and whiskers show the minimum–maximum across repeated runs, not across individual frames. '
                + 'Thin blue lines mark each Kotlin series’ baseline at the first Compose version. '
                + 'Tooltip changes compare point means with that baseline; green is better, red is worse. '
                + 'Percentage changes are unavailable when the baseline is missing or zero.');
        }
        return paragraphs;
    }

    function chartHeading(card, title, sourceRuns, metric, distribution = false) {
        const heading = element('div', null, card, 'chart-heading');
        element('h3', title, heading);
        const actions = element('div', null, heading, 'chart-actions');
        const info = element('button', 'ⓘ', actions, 'chart-raw-button');
        info.type = 'button';
        info.setAttribute('aria-label', 'About ' + title);
        info.setAttribute('aria-haspopup', 'dialog');
        info.setAttribute('aria-controls', infoDialog.id);
        info.addEventListener('click', () => {
            infoDialog.replaceChildren();
            const header = element('div', null, infoDialog, 'raw-dialog-heading');
            element('h2', title, header).id = 'chart-info-title';
            const close = element('button', 'Close', header, 'chart-raw-button');
            close.type = 'button';
            close.addEventListener('click', () => infoDialog.close());
            chartExplanation(metric, distribution).forEach(text => element('p', text, infoDialog));
            infoDialog.showModal();
        });
        const button = element('button', 'raw', actions, 'chart-raw-button');
        button.type = 'button';
        button.setAttribute('aria-label', 'Raw JSON reports for ' + title);
        button.addEventListener('click', () => {
            rawDialog.replaceChildren();
            const header = element('div', null, rawDialog, 'raw-dialog-heading');
            element('h2', 'Raw reports', header).id = 'raw-dialog-title';
            const close = element('button', 'Close', header, 'chart-raw-button');
            close.type = 'button';
            close.addEventListener('click', () => rawDialog.close());
            element('p', test.dataset.value + ' — ' + title, rawDialog);
            const runs = sourceRuns().filter(run => run.reportPath);
            const unique = [...new Map(runs.map(run => [run.reportPath, run])).values()];
            if (!unique.length) element('p', 'No raw reports for the currently visible series.', rawDialog);
            else {
                const list = element('ul', null, rawDialog, 'raw-report-list');
                unique.forEach(run => {
                    const item = element('li', null, list);
                    element('small', 'Kotlin ' + run.kotlin + ' / Compose ' + run.compose + ' / run ' + run.iteration, item);
                    const link = element('a', run.reportPath.split('/').pop(), item);
                    link.setAttribute('href', './' + run.reportPath.split('/').map(encodeURIComponent).join('/'));
                    link.target = '_blank';
                    link.rel = 'noopener';
                });
            }
            rawDialog.showModal();
        });
    }

    function options(control, values, onChange, labelFor = value => value) {
        const previous = control.dataset.value;
        control.dataset.value = values.includes(previous) ? previous : (values[0] ?? '');
        control.replaceChildren();
        values.forEach(value => {
            const label = element('label', null, control);
            const input = element('input', null, label);
            input.type = 'radio';
            input.name = control.id;
            input.value = value;
            input.checked = value === control.dataset.value;
            input.addEventListener('change', () => {
                if (!input.checked) return;
                control.dataset.value = value;
                onChange();
            });
            element('span', labelFor(value), label);
        });
    }

    function color(kotlin) {
        const hue = kotlinVersions.indexOf(kotlin) * 137.508 % 360;
        return `hsl(${hue}, 68%, 42%)`;
    }

    const minMaxWhiskers = {
        id: 'minMaxWhiskers',
        // Include the extremes, not just the mean, when Chart.js chooses axis limits.
        afterDataLimits(chart, { scale }) {
            if (scale.axis !== 'y') return;
            chart.data.datasets.forEach((dataset, index) => {
                if (!chart.isDatasetVisible(index)) return;
                dataset.ranges.forEach(range => {
                    if (!range) return;
                    scale.min = Math.min(scale.min, range.min);
                    scale.max = Math.max(scale.max, range.max);
                });
            });
        },
        beforeDatasetsDraw(chart) {
            const { ctx, chartArea, scales: { y } } = chart;
            ctx.save();
            ctx.beginPath();
            ctx.rect(chartArea.left, chartArea.top, chartArea.width, chartArea.height);
            ctx.clip();
            chart.data.datasets.forEach((dataset, index) => {
                if (!chart.isDatasetVisible(index)) return;
                const points = chart.getDatasetMeta(index).data;
                ctx.strokeStyle = dataset.borderColor;
                ctx.lineWidth = 2;
                dataset.ranges.forEach((range, i) => {
                    if (!range || points[i].skip) return;
                    const x = points[i].x;
                    const top = y.getPixelForValue(range.max);
                    const bottom = y.getPixelForValue(range.min);
                    ctx.beginPath();
                    ctx.moveTo(x, top);
                    ctx.lineTo(x, bottom);
                    ctx.moveTo(x - 6, top);
                    ctx.lineTo(x + 6, top);
                    ctx.moveTo(x - 6, bottom);
                    ctx.lineTo(x + 6, bottom);
                    ctx.stroke();
                });
            });
            ctx.restore();
        }
    };

    const baselineLines = {
        id: 'baselineLines',
        beforeDatasetsDraw(chart) {
            const { ctx, chartArea, scales: { y } } = chart;
            ctx.save();
            ctx.beginPath();
            ctx.rect(chartArea.left, chartArea.top, chartArea.width, chartArea.height);
            ctx.clip();
            ctx.strokeStyle = '#2563eb';
            ctx.lineWidth = 1;
            chart.data.datasets.forEach((dataset, index) => {
                if (!chart.isDatasetVisible(index) || !Number.isFinite(dataset.baseline)) return;
                const first = chart.getDatasetMeta(index).data[0];
                if (!first || first.skip) return;
                const baselineY = y.getPixelForValue(dataset.baseline);
                ctx.beginPath();
                ctx.moveTo(chartArea.left, baselineY);
                ctx.lineTo(chartArea.right, baselineY);
                ctx.stroke();
            });
            ctx.restore();
        }
    };

    const valueTooltip = element('div', null, document.body, 'value-tooltip');
    valueTooltip.hidden = true;
    valueTooltip.setAttribute('role', 'tooltip');
    const hideValueTooltip = () => { valueTooltip.hidden = true; };
    window.addEventListener('scroll', hideValueTooltip, true);
    window.addEventListener('resize', hideValueTooltip);

    function renderValueTooltip({ chart, tooltip }, metric, unit) {
        if (!tooltip.opacity || !tooltip.dataPoints?.length) {
            hideValueTooltip();
            return;
        }
        valueTooltip.replaceChildren();
        const format = value => value.toLocaleString(undefined, { maximumFractionDigits: 4 });
        element('strong', tooltip.title?.join(' — ') ?? '', valueTooltip);
        tooltip.dataPoints.forEach(point => {
            const { dataset, dataIndex } = point;
            const range = dataset.ranges[dataIndex];
            if (!range) return;
            const row = element('div', null, valueTooltip, 'value-tooltip-series');
            element('div', `${dataset.label}: mean ${format(range.mean)} ${unit}`, row);
            element('div', `Min–max: ${format(range.min)}–${format(range.max)} ${unit}`, row);
            element('div', `Runs: ${range.count}`, row);
            const baseline = dataset.baseline;
            const label = `vs ${chart.data.labels[0]} baseline`;
            if (!Number.isFinite(baseline) || baseline === 0) {
                element('div', `Change ${label}: unavailable (baseline missing or zero)`, row);
                return;
            }
            const change = (range.mean - baseline) / Math.abs(baseline) * 100;
            if (!Number.isFinite(change)) {
                element('div', `Change ${label}: unavailable`, row);
                return;
            }
            const better = higherIsBetter(metric) ? change > 0 : change < 0;
            const direction = change === 0 ? '' : better ? ' — better' : ' — worse';
            const percent = change.toLocaleString(undefined, { maximumFractionDigits: 2, signDisplay: 'exceptZero' });
            element('div', `${percent}% ${label}${direction}`, row,
                change === 0 ? '' : better ? 'baseline-better' : 'baseline-worse');
        });
        valueTooltip.hidden = false;
        const bounds = chart.canvas.getBoundingClientRect();
        valueTooltip.style.left = Math.max(8, Math.min(bounds.left + tooltip.caretX + 12,
            window.innerWidth - valueTooltip.offsetWidth - 8)) + 'px';
        valueTooltip.style.top = Math.max(8, Math.min(bounds.top + tooltip.caretY + 12,
            window.innerHeight - valueTooltip.offsetHeight - 8)) + 'px';
    }

    function selectedPlatform(run) {
        return run.target === target.dataset.value
            && (browserControl.hidden || run.browser === browser.dataset.value);
    }

    function updateBrowsers() {
        const runs = [...configurations, ...report.runs].filter(run => run.target === target.dataset.value);
        browserControl.hidden = !['js', 'wasmJs'].includes(target.dataset.value);
        options(browser, distinct(runs.map(run => run.browser).filter(Boolean)), updateModes,
            value => runs.find(run => run.browser === value).browserName || value);
        updateModes();
    }

    function updateModes() {
        renderEnvironment();
        options(mode, modes.filter(type => report.runs.some(run =>
            selectedPlatform(run) && run.type === type && run.measurements.length > 0
        )), updateTests);
        updateTests();
    }

    function renderEnvironment() {
        const container = document.getElementById('environment-info');
        container.replaceChildren();
        const selected = configurations.filter(selectedPlatform);
        if (!selected.length) {
            element('p', 'Environment metadata is unavailable in this report.', container, 'environment-empty');
            return;
        }
        const labels = {
            configuration: 'Configuration', launcher: 'Build and launch', runtime: 'Target process',
            target: 'Target', kotlinVersion: 'Kotlin', composeVersion: 'Compose', localCompose: 'Local Compose',
            browser: 'Browser', kind: 'Family', executable: 'Executable',
            capturedAt: 'Launcher snapshot (UTC)', buildProfile: 'Build profile', optimization: 'Optimization',
            coreCommit: 'Core commit', coreDirty: 'Core has uncommitted changes', browserVersion: 'Browser version',
            simulator: 'Simulator', host: 'Host', xcodeVersion: 'Xcode', sdkVersion: 'SDK version',
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
        const flatten = (value, path = [], rows = new Map()) => {
            if (value == null || (Array.isArray(value) && !value.length)) return rows;
            if (typeof value === 'object' && !Array.isArray(value)) {
                Object.entries(value).forEach(([key, child]) => flatten(child, [...path, key], rows));
            } else {
                let text;
                if (Array.isArray(value)) text = value.join('\n');
                else if (typeof value === 'boolean') text = value ? 'Yes' : 'No';
                else if (path.at(-1).endsWith('Bytes') && typeof value === 'number') {
                    text = (value / 1024 ** 3).toLocaleString(undefined, { maximumFractionDigits: 2 })
                        + ' GiB (' + value.toLocaleString() + ' bytes)';
                } else text = String(value);
                rows.set(path.join('.'), text);
            }
            return rows;
        };
        const values = selected.map(config => flatten(config.metadata));
        const keys = distinct(values.flatMap(rows => [...rows.keys()]));
        const scroll = element('div', null, container, 'environment-scroll');
        scroll.tabIndex = 0;
        scroll.setAttribute('role', 'region');
        scroll.setAttribute('aria-label', 'Environment comparison table');
        const table = element('table', null, scroll, 'environment-table');
        const head = element('tr', null, element('thead', null, table));
        element('th', 'Parameter', head).scope = 'col';
        selected.forEach(config => {
            const th = element('th', null, head);
            th.scope = 'col';
            element('span', 'Kotlin ' + config.kotlin, th);
            element('small', 'Compose ' + config.compose, th);
        });
        const body = element('tbody', null, table);
        for (const group of distinct(keys.map(key => key.split('.')[0]))) {
            const groupRow = element('tr', null, body, 'environment-group');
            const groupHeader = element('th', labels[group] || group, groupRow);
            groupHeader.colSpan = selected.length + 1;
            for (const key of keys.filter(key => key.split('.')[0] === group)) {
                const row = element('tr', null, body, /\.(warnings|error)$/.test(key) ? 'environment-warning' : '');
                const path = key.split('.').slice(1);
                const name = path.map(part => labels[part] || part).join(' / ') || labels[group];
                element('th', name, row).scope = 'row';
                const cells = values.map(rows => rows.get(key) ?? '—');
                if (cells.every(value => value === cells[0])) {
                    element('td', cells[0], row).colSpan = selected.length;
                } else cells.forEach(value => element('td', value, row));
            }
        }
        element('p', 'Memory and CPU values describe what the process can see. Browser memory is approximate; '
            + 'simulator hardware may describe its host. JVM heap is not physical RAM.', container, 'environment-note');
    }

    function updateTests() {
        options(test, distinct(report.runs.filter(run =>
            selectedPlatform(run) && run.type === mode.dataset.value
        ).map(run => run.name)), render);
        render();
    }

    function renderTestInfo(name, runs, section) {
        const heading = element('div', null, section, 'test-heading');
        element('h2', name, heading);
        const button = element('button', 'info', heading, 'test-info-button');
        button.type = 'button';
        button.setAttribute('aria-label', 'Parameters for ' + name);
        button.setAttribute('aria-expanded', 'false');
        const panel = element('div', null, section, 'test-parameters');
        panel.id = 'test-parameters-' + charts.children.length;
        panel.hidden = true;
        button.setAttribute('aria-controls', panel.id);
        button.addEventListener('click', () => {
            panel.hidden = !panel.hidden;
            button.setAttribute('aria-expanded', String(!panel.hidden));
        });

        const parameters = Object.assign({}, ...runs.map(run => ({ type: run.type, ...run.parameters })));
        const scroll = element('div', null, panel, 'environment-scroll');
        scroll.tabIndex = 0;
        scroll.setAttribute('role', 'region');
        scroll.setAttribute('aria-label', name + ' parameters');
        const table = element('table', null, scroll, 'environment-table test-parameters-table');
        const head = element('tr', null, element('thead', null, table));
        element('th', 'Parameter', head).scope = 'col';
        element('th', 'Value', head).scope = 'col';
        const body = element('tbody', null, table);
        const preferred = ['type', 'width', 'height', 'targetFps', 'warmupCount', 'frameCount', 'repeat', 'timeoutSeconds'];
        const keys = Object.keys(parameters);
        const ordered = [...preferred.filter(key => keys.includes(key)), ...keys.filter(key => !preferred.includes(key))];
        const format = value => value == null ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value);
        ordered.forEach(key => {
            const row = element('tr', null, body);
            element('th', key, row).scope = 'row';
            element('td', format(parameters[key]), row);
        });
    }

    function render() {
        hideValueTooltip();
        instances.forEach(chart => chart.destroy());
        instances = [];
        distributionCards = [];
        charts.replaceChildren();
        if (!target.dataset.value || !mode.dataset.value || !test.dataset.value) {
            message.textContent = 'No measurements were collected in this session. See summary.json and logs for errors.';
            return;
        }
        if (typeof Chart === 'undefined') {
            message.textContent = 'The embedded Chart.js library could not be loaded. Regenerate this report and reload the file.';
            return;
        }
        const runs = report.runs.filter(run =>
            selectedPlatform(run) && run.type === mode.dataset.value && run.name === test.dataset.value
        );
        message.textContent = '';
        for (const name of distinct(runs.map(run => run.name))) {
            const testRuns = runs.filter(run => run.name === name);
            const section = element('section', null, charts);
            renderTestInfo(name, testRuns, section);
            const failures = testRuns.filter(run => run.status !== 'passed' || run.measurements.length === 0);
            if (failures.length) {
                const details = element('details', null, section, 'warning');
                element('summary', `${failures.length} runs failed or have no measurements`, details);
                const list = element('ul', null, details);
                failures.forEach(run => element('li',
                    `Kotlin ${run.kotlin} / Compose ${run.compose} / run ${run.iteration}: ${run.error || run.status}`, list));
            }
            const metricNames = distinct(testRuns.flatMap(run => run.measurements.map(metric => metric.title)));
            if (!metricNames.length) element('p', 'No measurements available for this test.', section);
            const grid = element('div', null, section, 'chart-grid');
            for (const title of metricNames) {
                const unit = testRuns.flatMap(run => run.measurements).find(metric => metric.title === title).unit;
                const card = element('article', null, grid, 'chart-card');
                let chart;
                chartHeading(card, `${title} (${unit})`, () => testRuns.filter(run =>
                    report.composeVersions.includes(run.compose)
                    && run.measurements.some(metric => metric.title === title && Number.isFinite(metric.value))
                    && chart.data.datasets.some((dataset, index) => dataset.seriesKey === run.kotlin && chart.isDatasetVisible(index))
                ), title);
                const container = element('div', null, card, 'canvas-container');
                const canvas = element('canvas', null, container);
                canvas.setAttribute('role', 'img');
                canvas.setAttribute('aria-label', `${name}: ${title}, ${unit}, by Compose version`);
                const series = distinct(testRuns.filter(run => run.measurements.some(metric => metric.title === title)).map(run => run.kotlin));
                const datasets = series.map(kotlin => {
                    const ranges = report.composeVersions.map(compose => {
                        const values = testRuns.filter(run => run.kotlin === kotlin && run.compose === compose)
                            .map(run => run.measurements.find(metric => metric.title === title)?.value)
                            .filter(Number.isFinite);
                        if (!values.length) return null;
                        return {
                            mean: values.reduce((sum, value) => sum + value / values.length, 0),
                            min: Math.min(...values),
                            max: Math.max(...values),
                            count: values.length
                        };
                    });
                    return {
                        seriesKey: kotlin,
                        label: `Kotlin ${kotlin}`,
                        data: ranges.map(range => range?.mean ?? null),
                        baseline: ranges[0]?.mean ?? null,
                        ranges,
                        borderColor: color(kotlin),
                        backgroundColor: color(kotlin),
                        borderWidth: 2,
                        pointRadius: 4,
                        pointHoverRadius: 6,
                        hidden: hidden.has(kotlin),
                        spanGaps: false
                    };
                });
                instances.push(chart = new Chart(canvas, {
                    type: 'line',
                    data: { labels: report.composeVersions, datasets },
                    plugins: [baselineLines, minMaxWhiskers],
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        animation: false,
                        interaction: { mode: 'index', intersect: false },
                        scales: {
                            x: { offset: true, title: { display: true, text: 'Compose version' } },
                            y: { beginAtZero: true, title: { display: true, text: unit } }
                        },
                        plugins: {
                            tooltip: {
                                enabled: false,
                                external: context => renderValueTooltip(context, title, unit)
                            },
                            legend: { display: false }
                        }
                    }
                }));
            }
            renderDistributions(testRuns, section);
        }
        // Earlier charts may have been sized before their grid neighbors were added.
        instances.forEach(chart => chart.resize());
    }

    function renderDistributions(runs, section) {
        const titles = distinct(runs.flatMap(run => (run.distributions ?? []).map(metric => metric.title)));
        if (!titles.length) return;
        element('h2', 'Per-frame percentiles', section);
        for (const kotlin of kotlinVersions) {
            const kotlinRuns = runs.filter(run => run.kotlin === kotlin);
            if (!kotlinRuns.some(run => run.distributions?.some(metric => metric.values.length))) continue;
            const grid = element('div', null, section, 'chart-grid');
            for (const title of titles) {
                const series = report.composeVersions.map((compose, index) => ({
                    compose,
                    hue: index * 137.508 % 360,
                    values: kotlinRuns.filter(run => run.compose === compose)
                        .flatMap(run => run.distributions?.find(metric => metric.title === title)?.values ?? [])
                        .filter(Number.isFinite)
                })).filter(series => series.values.length);
                if (!series.length) continue;
                const unit = kotlinRuns.flatMap(run => run.distributions ?? []).find(metric => metric.title === title).unit;
                const datasets = series.map(({ compose, hue, values }) => {
                    const sorted = values.slice().sort((a, b) => a - b);
                    const data = Array.from({ length: 101 }, (_, percentile) => {
                        const index = (sorted.length - 1) * percentile / 100;
                        const low = sorted[Math.floor(index)];
                        const high = sorted[Math.ceil(index)];
                        const value = low + (high - low) * (index - Math.floor(index));
                        return { x: percentile, y: value > 0 ? value : null };
                    });
                    return {
                        seriesKey: kotlin,
                        label: `Compose ${compose} (n=${values.length})`,
                        composeVersion: compose,
                        data,
                        borderColor: `hsl(${hue}, 68%, 42%)`,
                        backgroundColor: `hsl(${hue}, 68%, 42%)`,
                        borderWidth: 2,
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        tension: 0,
                        hidden: hidden.has(kotlin)
                    };
                });
                const card = element('article', null, grid, 'chart-card');
                card.hidden = hidden.has(kotlin);
                distributionCards.push({ kotlin, card });
                let chart;
                chartHeading(card, `${title} (${unit}) — Kotlin ${kotlin}`, () => kotlinRuns.filter(run =>
                    !hidden.has(kotlin)
                    && run.distributions?.some(metric => metric.title === title && metric.values.some(Number.isFinite))
                    && chart.data.datasets.some((dataset, index) => dataset.composeVersion === run.compose && chart.isDatasetVisible(index))
                ), title, true);
                const container = element('div', null, card, 'canvas-container');
                const canvas = element('canvas', null, container);
                canvas.setAttribute('role', 'img');
                canvas.setAttribute('aria-label', `${test.dataset.value}: ${title} percentiles, Kotlin ${kotlin}, by Compose version`);
                instances.push(chart = new Chart(canvas, {
                    type: 'line',
                    data: { datasets },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        animation: false,
                        interaction: { mode: 'index', intersect: false },
                        scales: {
                            x: { type: 'linear', min: 0, max: 100, title: { display: true, text: 'Percentile' }, ticks: { stepSize: 10 } },
                            y: { type: 'logarithmic', title: { display: true, text: `${title} (${unit})` } }
                        },
                        plugins: {
                            tooltip: {
                                callbacks: {
                                    title: items => items.length ? `P${items[0].parsed.x}` : '',
                                    label: context => `${context.dataset.label}: ${context.parsed.y.toLocaleString(undefined, { maximumFractionDigits: 4 })} ${unit}`
                                }
                            }
                        }
                    }
                }));
            }
        }
    }

    const kotlinFilter = document.getElementById('kotlin-versions');
    kotlinVersions.forEach(kotlin => {
        const label = element('label', null, kotlinFilter, 'kotlin-option');
        const input = element('input', null, label);
        input.type = 'checkbox';
        input.value = kotlin;
        input.checked = true;
        const swatch = element('span', null, label, 'color-swatch');
        swatch.style.backgroundColor = color(kotlin);
        swatch.setAttribute('aria-hidden', 'true');
        element('span', kotlin, label);
        input.addEventListener('change', () => {
            if (input.checked) hidden.delete(kotlin); else hidden.add(kotlin);
            hideValueTooltip();
            distributionCards.forEach(({ kotlin, card }) => { card.hidden = hidden.has(kotlin); });
            instances.forEach(chart => {
                chart.data.datasets.forEach((dataset, index) => {
                    chart.setDatasetVisibility(index, !hidden.has(dataset.seriesKey));
                });
                chart.resize();
                chart.update();
            });
        });
    });

    if (report.warnings.length) {
        const details = element('details', null, document.getElementById('warnings'), 'warning');
        element('summary', `${report.warnings.length} report warnings`, details);
        const list = element('ul', null, details);
        report.warnings.forEach(warning => element('li', warning, list));
    }
    options(target, distinct([...configurations, ...report.runs].map(run => run.target)), updateBrowsers);
    updateBrowsers();
})();
