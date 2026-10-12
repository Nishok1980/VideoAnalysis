"""CI end-to-end check: real upload, analysis, preview, export and deletion."""
import json
import os
import time
import urllib.request
import urllib.error
from pathlib import Path

base = "http://127.0.0.1:8080"
def call(path, method="GET", data=None, content_type="application/json"):
    if data is not None and not isinstance(data, bytes):
        data = json.dumps(data).encode()
    req = urllib.request.Request(base + path, data=data, method=method,
        headers={"X-VideoAnalysis":"1", "Content-Type":content_type})
    with urllib.request.urlopen(req, timeout=90) as response:
        raw = response.read()
        return json.loads(raw) if raw else None

for attempt in range(60):
    try:
        status = call("/api/status")
        break
    except (OSError, urllib.error.URLError):
        time.sleep(1)
else:
    raise RuntimeError("Application did not start")
assert status["videoToolsReady"] and status["modelPresent"]
# Cross-origin mutations must be refused before multipart parsing.
request = urllib.request.Request(base+"/api/videos", method="POST",
    headers={"Origin":"https://unrelated.example"}, data=b"")
try:
    urllib.request.urlopen(request)
    raise AssertionError("Cross-origin mutation was accepted")
except urllib.error.HTTPError as error:
    assert error.code == 403

boundary = "VideoAnalysisSmokeBoundary"
clip = Path(os.environ["VIDEO_PEOPLE_CLIP"]).read_bytes()
body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
        f'filename="people.mp4"\r\nContent-Type: video/mp4\r\n\r\n').encode()
body += clip + f'\r\n--{boundary}--\r\n'.encode()
video = call("/api/videos", "POST", body, f"multipart/form-data; boundary={boundary}")
rules = {"areaName":"Fixture", "area":{"x":0,"y":0,"width":1,"height":1},
    "recordingStart":"2026-10-12T23:00:00+05:30", "fromTime":"23:00",
    "toTime":"05:00", "days":[], "presenceSeconds":1, "lingerSeconds":2,
    "entryCount":3, "entryWindowSeconds":10, "interactionSeconds":2,
    "proximity":0.08, "confidence":0.45}
job = call("/api/jobs", "POST", {"videoId":video["id"],"rules":rules})
for attempt in range(120):
    job = call("/api/jobs/"+job["id"])
    if job["state"] in ("FAILED","CANCELLED","COMPLETED"):
        break
    time.sleep(1)
assert job["state"] == "COMPLETED", job
assert job["rules"]["recordingStart"].endswith("+05:30"), job
assert job["events"], job
assert job["previewReady"]
event = job["events"][0]
call(f'/api/jobs/{job["id"]}/events/{event["id"]}',"PUT",{"review":"RELEVANT"})
export = call(f'/api/jobs/{job["id"]}/export')
assert export["schemaVersion"] == 1
assert export["analysis"]["events"][0]["review"] == "RELEVANT"
assert len(export["analysis"]["modelSha256"]) == 64
request = urllib.request.Request(base+f'/api/videos/{video["id"]}/preview.mp4',
    headers={"Range":"bytes=0-31"})
with urllib.request.urlopen(request) as response:
    assert response.status == 206, response.status
    assert len(response.read()) == 32
call('/api/videos/'+video['id'], 'DELETE')
assert call('/api/videos') == []
assert call('/api/jobs') == []
print('PASS: upload, real inference, events, review, JSON, range playback, origin rejection and deletion')
