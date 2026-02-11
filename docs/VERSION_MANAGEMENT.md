# Version Management Feature

## Overview

This plugin now supports version management for static pages. Every upload creates a new version, allowing you to:
- Keep multiple versions of your static site
- Switch between versions easily
- Automatically clean up old versions based on a configurable limit

## How It Works

### Version Storage

- Each upload creates a new version stored in: `static/{projectDir}/versions/version-{N}/`
- Version numbers are sequential (1, 2, 3, ...)
- The active version's files are copied to the project root directory for serving

### Version Activation

When you activate a version:
1. The previous active version is deactivated
2. Files from the new active version are copied to the project root
3. Your site immediately serves the new version's content

### Automatic Cleanup

- Configure `maxVersions` in project settings (default: 10)
- When uploading exceeds the limit, oldest versions are automatically deleted
- The active version is never automatically deleted
- Set `maxVersions` to 0 for unlimited versions

## API Endpoints

### List Versions
```
GET /apis/console.api.staticpage.halo.run/v1alpha1/projects/{name}/versions
```

Returns all versions for a project, sorted by version number (newest first).

### Activate Version
```
POST /apis/console.api.staticpage.halo.run/v1alpha1/projects/{name}/versions/{versionName}/activate
```

Activates the specified version. The version's files will be copied to the project root.

### Delete Version
```
DELETE /apis/console.api.staticpage.halo.run/v1alpha1/projects/{name}/versions/{versionName}
```

Deletes a specific version. Cannot delete the active version - you must activate another version first.

## CLI Usage

The CLI automatically creates a new version on each upload:

```bash
npx halo-static-pages-deploy-cli deploy \
  -e https://your-halo-site.com \
  -i project-xyz \
  -t your-token \
  -f ./dist
```

Each deployment creates a new version and, if it's the first version, activates it automatically.

## UI Features (To Be Implemented)

The UI will include:
1. **Versions Tab**: View all versions with creation time, size, and status
2. **Version Actions**: 
   - Activate/deactivate versions
   - Delete versions
   - View version details
3. **Settings**: Configure `maxVersions` limit

## Technical Details

### Version Schema

```typescript
interface ProjectVersion {
  metadata: {
    name: string;
    generateName: string;
  };
  spec: {
    projectName: string;        // Parent project
    version: number;            // Sequential version number
    displayName: string;        // e.g., "v1", "v2"
    directory: string;          // e.g., "versions/version-1"
    active: boolean;            // Is this the active version?
    size: number;               // Size in bytes
    creationTime: string;       // ISO timestamp
    description: string;        // Optional description
  };
  status: {
    lastModified: string;       // ISO timestamp
  };
}
```

### Project Schema Updates

```typescript
interface ProjectSpec {
  // ... existing fields ...
  maxVersions?: number;         // Default: 10, 0 = unlimited
}
```

## Migration Notes

- **Existing projects**: Continue to work as before
- **New uploads**: Automatically create versions
- **First version**: Automatically activated
- **File operations**: Work on the active version

## Best Practices

1. **Set appropriate maxVersions**: Balance between history and storage
2. **Test before activating**: Upload and test in a staging environment
3. **Document versions**: Use descriptive names in version metadata
4. **Regular cleanups**: Monitor and manually delete unneeded versions

## Troubleshooting

### Version not activating
- Ensure the version exists and is not already active
- Check server logs for permission issues
- Verify the versions directory structure

### Old versions not deleting
- Check that `maxVersions` is set correctly
- Verify versions are not marked as active
- Check file system permissions

### Storage issues
- Monitor disk usage with multiple versions
- Adjust `maxVersions` based on available storage
- Consider external storage solutions for large sites
