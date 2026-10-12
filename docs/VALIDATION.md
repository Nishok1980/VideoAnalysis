# Validation and camera acceptance

## Automated checks

`mvn clean verify` compiles all modules and runs deterministic tests of overnight
scheduling, minimum durations, track expiry, one-to-one matching, repeated
entries, proximity, model output coordinates and review preservation.

Three opt-in environment variables enable native integration tests:

- VIDEO_TEST_CLIP: absolute path to a short valid video, at least 1 second.
- VIDEO_TEST_MODEL: absolute path to the verified FP32 model.
- VIDEO_PEOPLE_CLIP: absolute path to a short clip with clearly visible people.

The workflow generates a blank video plus a short repeated-frame clip from
the public Ultralytics bus reference image. It checks FFmpeg probing,
thumbnail/preview creation, sampled frames, cancellation, actual ONNX person
inference and black-frame output. The reference image is a smoke fixture, not
a street-camera accuracy benchmark. Fixtures/model weights are not committed.

`scripts/smoke-api.py` runs against a local app started from the repository
root. It exercises the real HTTP flow: upload, analysis, nonempty findings,
review, JSON export, byte-range playback, cross-origin request rejection and
session deletion. It needs VIDEO_PEOPLE_CLIP and the installed model.

GitHub Actions additionally compiles/packages on Windows/JDK 25. Native
integration runs on Linux; verify FFmpeg and model loading on the user's
Windows device before calling that deployment accepted.

## Manual acceptance on the user's camera

Use short exports covering daytime, night, empty street, passing people,
someone lingering, dog alone, person near dog without feeding and actual
feeding if available. Draw the same area and manually label event intervals.

Record true alerts, false alerts and missed events separately per rule and
lighting condition. Tune duration/confidence/area against one set and then
check different clips. Do not estimate a success rate from only positive
examples or synthetic fixtures. Identity, intent and feeding are not outputs
of the current detector.

Verify that the recording start entered in the app matches the DVR clock and
that the played event interval aligns with the original recording. The fixed
2-FPS pipeline has approximately half-second sample resolution, with extra
tracking uncertainty. Variable-frame-rate/oddly timestamped DVR exports need
special attention during acceptance.

## Local execution environment

At implementation time the editing container supplied a Java runtime but no
javac or Maven; outbound Maven repository access timed out. Full compilation
and native integration are delegated to the repository's GitHub Actions
workflow. Check the actual Actions result for this commit; do not infer a
passing build from the presence of this document or the workflow alone.
