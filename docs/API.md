# API and report contract

Base URL: `http://localhost:8080/api`.
Mutations require `X-VideoAnalysis: 1` and same-origin requests. JSON bodies
use `Content-Type: application/json`. Video upload is multipart field `file`.
The UI is the supported first-use workflow; this API permits local integration.

| Method and path | Result |
| --- | --- |
| GET /status | Model file presence and video-tool availability |
| POST /videos | Store/validate multipart video; return VideoInfo |
| GET /videos | Uploaded videos in this session |
| DELETE /videos/{id} | Delete video and related jobs when no analysis is active |
| GET /videos/{id}/thumbnail.jpg | First video frame |
| GET /videos/{id}/preview.mp4 | Silent playback once ready; HTTP ranges supported |
| POST /jobs | Start analysis with `{videoId, rules}` |
| GET /jobs | Current session jobs |
| GET /jobs/{id} | Status, stage, progress, rules and findings |
| POST /jobs/{id}/cancel | Cooperative cancellation |
| PUT /jobs/{jobId}/events/{eventId} | Review with `{review: "RELEVANT"}` etc. |
| GET /jobs/{id}/export | Download versioned JSON report |

Example rules body (videoId must come from upload):

```json
{
  "videoId": "00000000-0000-0000-0000-000000000000",
  "rules": {
    "areaName": "Street entrance",
    "area": {"x": 0.1, "y": 0.2, "width": 0.7, "height": 0.7},
    "recordingStart": "2026-10-12T23:00:00+05:30",
    "fromTime": "23:00",
    "toTime": "05:00",
    "days": [],
    "presenceSeconds": 2,
    "lingerSeconds": 180,
    "entryCount": 3,
    "entryWindowSeconds": 300,
    "interactionSeconds": 3,
    "proximity": 0.08,
    "confidence": 0.45
  }
}
```

Use null for recordingStart/fromTime/toTime to analyse all clip positions
without a wall-clock schedule. Schedule selection requires recordingStart.
Weekday names, when used, are `MONDAY` through `SUNDAY`.

States: QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED.
Progress is analysis position / duration; preview preparation has a stage
message and no percent estimate. FAILED/CANCELLED exports contain partial
findings and retain the state. Jobs are not resumed after restart.

Review values: UNREVIEWED, RELEVANT, FALSE_ALERT, UNCERTAIN.
Event fields include id, type, local trackId, startSeconds, endSeconds,
observedAt (nullable), reason, detectionConfidence, box and review.
Coordinates in box refer to the latest person detection for that event.
The current UI does not overlay moving detections during playback.

Export envelope fields: schemaVersion=1, analysis, sampleFramesPerSecond=2,
notice. The analysis includes job/video IDs, submitted rules, current events,
previewReady and modelSha256. Videos are not embedded in the JSON. Reports
are downloadable but not currently importable into another app session.
