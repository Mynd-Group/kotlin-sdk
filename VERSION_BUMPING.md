# Version Bumping

Uses [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`
- **PATCH**: Bug fixes (1.1.0 → 1.1.1)
- **MINOR**: New features (1.1.0 → 1.2.0)  
- **MAJOR**: Breaking changes (1.1.0 → 2.0.0)

## Process

1. Update version in `myndcoresdk/build.gradle.kts`:
   ```kotlin
   publishing {
       publications {
           register<MavenPublication>("release") {
               groupId = "com.github.Mynd-Group"
               artifactId = "kotlin-sdk"
               version = "1.2.0"  // Update this line
               
               afterEvaluate {
                   from(components["release"])
               }
           }
       }
   }
   ```

2. Commit and tag:
   ```bash
   git add myndcoresdk/build.gradle.kts
   git commit -m "Bump version to 1.2.0"
   git tag -a v1.2.0 -m "Release version 1.2.0"
   git push origin v1.2.0 main
   ```

3. JitPack will automatically build the release when the tag is pushed. Users can then update their dependency:
   ```kotlin
   dependencies {
       implementation("com.github.Mynd-Group:kotlin-sdk:v1.2.0")
   }
   ```

## Notes

- JitPack builds are triggered automatically by Git tags
- Use `v` prefix for tags (e.g., `v1.2.0`) to match JitPack conventions
- Verify the build status at https://jitpack.io/#Mynd-Group/kotlin-sdk
- Allow a few minutes for JitPack to process the new version after pushing the tag 