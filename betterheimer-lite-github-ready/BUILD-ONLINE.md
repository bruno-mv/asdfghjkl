# Build BetterHeimer Lite online with GitHub Actions

This folder is laid out as the **root of a GitHub repository**. The workflow in
`.github/workflows/build.yml` builds the mod on GitHub's hosted Linux runner using
Java 25 and Gradle 9.6.0, then uploads the build output as an Actions artifact.

## Quick setup

1. Create a repository at https://github.com/new. A private repository is fine.
2. Upload the **contents of this folder** to the repository root (not the ZIP file
   itself). Make sure `.github/workflows/build.yml` is present at that exact path.
3. Open the repository's **Actions** tab and enable Actions if prompted.
4. Select **Build Fabric mod** and click **Run workflow**, or make a commit/push
   to trigger the workflow automatically.
5. Open the completed workflow run. Under **Artifacts**, download
   `betterheimer-lite-build.zip`.
6. Inside the downloaded artifact, look in `build/libs/` for the mod JAR. If the
   build failed, open the workflow run and inspect the **Build mod** step's logs.

## Notes

- GitHub Actions builds the source; it does not guarantee the source is compatible.
  If the build fails, use the first meaningful compiler/dependency error from the
  log to fix the project.
- This project has not been independently compiled or tested in Minecraft here.
- Do not upload generated credentials, tokens, or private files to the repository.
