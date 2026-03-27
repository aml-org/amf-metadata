# amf-metadata

**Repository**: https://github.com/aml-org/amf-metadata

## Release Process
Follows the regular release process (see ~/mulesoft/CLAUDE.md).

## Release Position
- **Fourth** in the RC/release line
- Depends on **amf** RC/release

## Files & Locations

### Version Files
- `transform/dependencies.properties` - contains dependencies
- `versions.yaml` - contains project versions

### Dependencies
- amf (update to RC version for RC, release version for release)
- amf-rdf (part of amf-aml)

## Artifacts Published
This project publishes 2 artifacts:
- **amf-vocabulary** - Uses **MAJOR** version bumps (e.g., 67.0.0 → 68.0.0)
- **amf-transform** - Uses **MINOR** version bumps (e.g., 2.51.0 → 2.52.0)

### Versioning Rules
- **Vocabulary**: Always increment the MAJOR version (x.0.0)
- **Transform**: Always increment the MINOR version (x.y.0)
- **Example**: After releasing 67.0.0 / 2.51.0, next snapshot is 68.0.0-SNAPSHOT / 2.52.0-SNAPSHOT

## Jenkins Configuration

### Dashboard
- **Base URL**: https://jenkins.build.msap.io/job/application/job/AMF/job/amf-metadata
- **Branch URL format**: `{base-url}/job/{branch-name}` (URL encode `/` as `%252F`)
- **Example**: https://jenkins.build.msap.io/job/application/job/AMF/job/amf-metadata/job/release%252F67.0.0-2.51.0

### Stages to Modify
- **Building stages**: N/A
- **Publishing stages**: `Publish Vocabulary Artifact`, `Publish Transform Artifact`

### Jenkinsfile Updates
- **For RC**: Add `"release/*"` to branch list in `Publish Vocabulary Artifact` and `Publish Transform Artifact` stages
- **For Release**: Remove `"release/*"` from branch list in publish stages

## Special Instructions

### Manual Tagging (IMPORTANT)
**After merging to master, manually tag BOTH artifacts with namespaced tags:**
```bash
git tag vocabulary/vocab-version origin/master
git tag transform/transform-version origin/master
git push origin vocabulary/vocab-version transform/transform-version
```
**Example**: For vocabulary 67.0.0 and transform 2.51.0:
```bash
git tag vocabulary/67.0.0 origin/master
git tag transform/2.51.0 origin/master
git push origin vocabulary/67.0.0 transform/2.51.0
```
**⚠️ IMPORTANT**: Use namespaced tags (`vocabulary/` and `transform/` prefixes), NOT plain version numbers!

## Dependencies Impact
After releasing amf-metadata, update the version in:
- amf-service (post-release action - update amf-transform version)
- amf-tckutor (post-release action)
