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
    const kotlinVersions = distinct(report.runs.map(run => run.kotlin));
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
        const runs = report.runs.filter(run => run.target === target.dataset.value);
        browserControl.hidden = !['js', 'wasmJs'].includes(target.dataset.value);
        options(browser, distinct(runs.map(run => run.browser).filter(Boolean)), updateModes,
            value => runs.find(run => run.browser === value).browserName || value);
        updateModes();
    }

    function updateModes() {
        options(mode, modes.filter(type => report.runs.some(run =>
            selectedPlatform(run) && run.type === type && run.measurements.length > 0
        )), updateTests);
        updateTests();
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
    options(target, distinct(report.runs.filter(run => run.measurements.length > 0).map(run => run.target)), updateBrowsers);
    updateBrowsers();
})();
