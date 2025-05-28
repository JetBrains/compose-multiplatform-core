config.devtool = undefined; // default is `eval-source-map`
config.mode = 'none'; // default is `development` (for jsBrowserRun).


config.devServer = {
  ...(config.devServer || {}),
  compress: true,
  // WARNING: disableHostCheck is removed in Webpack 5.
  // Use allowedHosts instead if needed.
  allowedHosts: "all",
};
