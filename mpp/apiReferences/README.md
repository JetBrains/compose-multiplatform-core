Building the API references with Material3 Stories included:

```shell
./gradlew :mpp:apiReferences:buildApiReferencesWithStories -PapiReferences.storiesRootPath=/stories
```

The output is in `.../out/androidx/mpp/apiReferences/build/dokka/html`.
You can test it locally by running in the output directory:

```shell
python3 -m http.server
```