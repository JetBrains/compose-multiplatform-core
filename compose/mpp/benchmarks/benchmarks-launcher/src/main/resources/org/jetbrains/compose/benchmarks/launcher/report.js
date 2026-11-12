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
        const container = document.getElementById('environment');
        container.replaceChildren();
        const selected = configurations.filter(selectedPlatform);
        element('h2', 'Build and target environment', container);
        if (!selected.length) {
            element('p', 'Environment metadata is unavailable in this report.', container, 'environment-empty');
            return;
        }
        const labels = {
            configuration: 'Configuration', launcher: 'Build and launch', runtime: 'Target process',
            target: 'Target', kotlinVersion: 'Kotlin', composeVersion: 'Compose', localCompose: 'Local Compose',
            composeCorePath: 'Core checkout', browser: 'Browser', kind: 'Family', executable: 'Executable',
            capturedAt: 'Launcher snapshot (UTC)', buildProfile: 'Build profile', optimization: 'Optimization',
            coreCommit: 'Core commit', coreDirty: 'Core has uncommitted changes', browserVersion: 'Browser version',
            simulator: 'Simulator', host: 'Host', xcodeVersion: 'Xcode', sdkVersion: 'SDK version',
            osName: 'OS', osVersion: 'OS version',
            architecture: 'Process architecture', cpuModel: 'CPU', availableProcessors: 'Available logical processors',
            physicalMemoryBytes: 'Physical memory (process-reported)', id: 'ID', name: 'Name', state: 'Initial state',
            deviceTypeIdentifier: 'Device type', runtimeIdentifier: 'Runtime identifier', runtimeVersion: 'Runtime version',
            runtimeBuild: 'Runtime build', schemaVersion: 'Schema version', deviceModel: 'Hardware model',
            simulatorModel: 'Simulated device model', gpuName: 'GPU / renderer', jvmName: 'JVM', jvmVersion: 'JVM version',
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

    function render() {
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
            element('h2', name, section);
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
                element('h3', `${title} (${unit})`, card);
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
                instances.push(new Chart(canvas, {
                    type: 'line',
                    data: { labels: report.composeVersions, datasets },
                    plugins: [minMaxWhiskers],
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
                                callbacks: {
                                    label(context) {
                                        const range = context.dataset.ranges[context.dataIndex];
                                        const format = value => value.toLocaleString(undefined, { maximumFractionDigits: 4 });
                                        return [
                                            `${context.dataset.label}: mean ${format(range.mean)} ${unit}`,
                                            `Min–max: ${format(range.min)}–${format(range.max)} ${unit}`,
                                            `Runs: ${range.count}`
                                        ];
                                    }
                                }
                            },
                            legend: { display: false }
                        }
                    }
                }));
            }
            if (mode.dataset.value === 'fps') renderDistributions(testRuns, section);
        }
        // Earlier charts may have been sized before their grid neighbors were added.
        instances.forEach(chart => chart.resize());
    }

    function renderDistributions(runs, section) {
        const titles = distinct(runs.flatMap(run => (run.distributions ?? []).map(metric => metric.title)));
        if (!titles.length) return;
        element('h2', 'Per-frame percentiles', section);
        element('p', 'X: percentile (0–100). Y: metric value (logarithmic scale). Repeated runs are pooled. '
            + 'Percentiles use linear interpolation between sorted frame values; P0 is the minimum and P100 is the maximum. '
            + 'Non-positive percentile values cannot be shown on a logarithmic scale and are omitted.', section);
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
                element('h3', `${title} (${unit}) — Kotlin ${kotlin}`, card);
                const container = element('div', null, card, 'canvas-container');
                const canvas = element('canvas', null, container);
                canvas.setAttribute('role', 'img');
                canvas.setAttribute('aria-label', `${test.dataset.value}: ${title} percentiles, Kotlin ${kotlin}, by Compose version`);
                instances.push(new Chart(canvas, {
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
