# Build

Use a JDK that supports `javac --release 8`, Android SDK API 19, Android build-tools with D8, and a local checkout of [HMG-API](https://github.com/VysseCodes/HMG-API). The build was checked against upstream commit `9b57914818e93cf62887349b60dacbca83396bfe`.

```sh
export HMG_API=/path/to/HMG-API
export ANDROID_JAR=/path/to/Android/sdk/platforms/android-19/android.jar
export D8=/path/to/Android/sdk/build-tools/35.0.0/d8
export SIGNING_KEY=/absolute/path/to/your.key
sh build.sh
```

Generate your own author key with HMG-API's signing tool. HMG-API checks that updates are signed by the original author. The release key stays with the maintainer. The supplied Java stubs are compile-only.

The checked-in recipe under `recipes/` is the native payload used by this release. Its corresponding ARM Thumb assembly and C source are in `native/`. See `native/README.md` for the recipe layout.
