
function configLaunchers(config) {
    config.customLaunchers = {
        ChromeForComposeTests: {
            base: "Chrome",
            flags: ["--no-sandbox", "--disable-search-engine-choice-screen"]
        },
        FirefoxForComposeTests: {
            base: "Firefox",
            prefs: {
                'dom.w3c_touch_events.enabled': 1
            }
        },
        SafariForComposeTests: {
            base: "Safari"
        }
    }

    config.browsers = [];
    if (process.env["jetbrains.androidx.web.tests.enableChrome"]) {
        config.browsers.push("ChromeForComposeTests");
    }
    if (process.env["jetbrains.androidx.web.tests.enableFirefox"]) {
        config.browsers.push("FirefoxForComposeTests");
    }
    if (process.env["jetbrains.androidx.web.tests.enableSafari"]) {
        config.browsers.push("SafariForComposeTests");
    }

    console.log("Browsers: " + config.browsers);
}

exports.configLaunchers = configLaunchers;