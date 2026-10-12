# VideoAnalysis

A local Java application for reviewing Hikvision/DVR recordings on Windows.
Upload video, select a rectangular area, set monitoring hours and inspect
explainable events with supporting playback. Phase 1 analyses uploaded files;
live DVR connections, Android and notifications are future work.

## What this version does

- Reads MP4, MKV, MOV and AVI recordings using FFmpeg/FFprobe.
- Detects people and dogs using a real YOLOv10 ONNX model on the CPU.
- Samples two frames per second and tracks detections over short intervals.
- Flags person presence, lingering, repeated area entries and person/dog
  proximity during a configurable monitoring window.
- Supports overnight schedules and an explicit recording start/UTC offset.
- Shows job progress, cancellation, errors, timed segment playback and reviews.
- Exports a versioned JSON report with UUIDs, rules, model SHA-256 and feedback.
- Runs on 127.0.0.1, with local media and a maximum of one active analysis.

**This version does not recognise feeding, criminal intent, or arbitrary
unusual behaviour.** Person/dog proximity is a candidate for human review.
Repeated entries can be different people or fragmented tracks; there is no
face recognition or persistent identity matching. No event is an accusation.

## 1. Windows prerequisites

Use Windows x64, a JDK (not just a JRE), Maven, Git and FFmpeg.
Java source targets 17. JDK 17/21/25 are practical choices; Spring Boot 4.0.8
also documents compatibility through Java 26. Native runtime compatibility
must still be checked on your machine. Docker, Python and a paid AI API are
not required to run the application.

Download links:

- JDK: https://adoptium.net/temurin/releases/
- Maven: https://maven.apache.org/download.cgi
- Maven installation: https://maven.apache.org/install.html
- FFmpeg Windows build links: https://ffmpeg.org/download.html#build-windows
- Git for Windows: https://git-scm.com/downloads/win

For FFmpeg, extract a Windows build that includes `libx264`, then add its
`bin` folder (containing both `ffmpeg.exe` and `ffprobe.exe`) to PATH.
Add Maven's `bin` folder to PATH too. Set JAVA_HOME to your JDK directory.
Close and reopen PowerShell after changing environment variables.

Check all prerequisites:

```powershell
java -version
javac -version
mvn -version
git --version
ffmpeg -version
ffprobe -version
```

If a command is not recognised, fix PATH before proceeding. Keep the app
bound to localhost; this initial version is not a remotely hosted service.

## 2. Get the project

The initial implementation is on `feature/windows-video-analysis`:

```powershell
cd C:\Users\nisho\AI_Worspace
git clone --branch feature/windows-video-analysis https://github.com/Nishok1980/VideoAnalysis.git
cd VideoAnalysis
```

For an existing clone:

```powershell
cd C:\Users\nisho\AI_Worspace\VideoAnalysis
git fetch origin
git switch feature/windows-video-analysis
git pull --ff-only
```

If local changes prevent a branch switch, keep them and review `git status`;
do not discard them just to follow these commands.

## 3. Install the detection model

From the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-model.ps1
```

This runs just this setup script without changing your persistent execution
policy. It downloads the model from Hugging Face and verifies the published
SHA-256. Internet access is needed for this download and the first Maven build.
After dependencies/model are installed, video analysis does not need internet.

If your organisation disallows scripts, download the file using the link in
[models/README.md](models/README.md), save as `models/yolov10n.onnx` and compare:

```powershell
Get-FileHash .\models\yolov10n.onnx -Algorithm SHA256
```

Expected: `a77dd863933f184a19e84361c64b788228a7c7dacc2c78939239a96ad3efca3b`.
These optional model weights are labelled AGPL-3.0 by their publisher. Review
model licensing before distributing a commercial product.

## 4. Build with Maven

Run from the root containing the parent `pom.xml`:

```powershell
mvn clean verify
```

All five modules should succeed. The executable file is:
`videoanalysis-app/target/videoanalysis-app-0.1.0-SNAPSHOT.jar`.
No Checkstyle gate is enabled in this initial version. Dependency versions
are centralised in the parent POM.

## 5. Run on Windows

Keep your current directory at the repository root:

```powershell
java --enable-native-access=ALL-UNNAMED -jar .\videoanalysis-app\target\videoanalysis-app-0.1.0-SNAPSHOT.jar
```

Or use the prerequisite-checking launcher:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-windows.ps1
```

Wait for `Started VideoAnalysisApplication`, then open:
**http://localhost:8080**

Ctrl+C in PowerShell stops the application. Export JSON before stopping.

### Maven run alternative

Do not run `spring-boot:run` on the aggregator root. Install the modules first,
then target the executable module:

```powershell
mvn clean install
mvn -pl videoanalysis-app spring-boot:run
```

The JAR command above is the recommended first run because the working
directory and executable module are explicit.

### IntelliJ IDEA

1. Open the root `pom.xml` as a Maven project.
2. Set Project SDK and Maven runner JDK to your installed JDK.
3. Reload all Maven projects and build.
4. Open `VideoAnalysisApplication.java` in `videoanalysis-app`.
5. Set its run configuration working directory to the **repository root**.
6. Optionally add VM option `--enable-native-access=ALL-UNNAMED`.
7. Run the main class and open http://localhost:8080.

This is a Java/Spring application, not an Android Studio project.

## 6. First camera test

1. Export a 1–3 minute recording from your Hikvision DVR using its Windows
   software. Choose ordinary MP4/H.264 where available; proprietary DAV exports
   are not accepted in this version.
2. Open the app and confirm the setup message says it is ready.
3. Upload the recording, then draw a rectangle around the entrance/street.
4. Enter the recording's actual start date/time, and offset `+05:30` for India.
   File modification time and burned-in camera text are not read as timestamps.
5. To test night rules, select monitoring hours, for example 23:00 to 05:00.
   Leave the schedule unchecked for a daytime test video.
6. For a quick test, set presence to 2 seconds and lingering to 10 seconds.
7. Analyse. The app first prepares silent browser-compatible playback, then
   runs detection. Preparation is reported as a stage, without a fake percentage.
8. Open a finding, play its segment, and mark it relevant, false alert or uncertain.
9. Export JSON. Delete the uploaded video when finished.

If no event appears, check the selected area, schedule, recording start,
threshold, visibility and duration. A zero-event report does not prove that
no activity occurred. Use original playback to evaluate missed detections.

## 7. Configuration

Defaults are in `videoanalysis-app/src/main/resources/application.properties`.
Environment variables are resolved when the app starts:

| Variable | Default | Purpose |
| --- | --- | --- |
| VIDEO_FFMPEG | ffmpeg | FFmpeg executable path |
| VIDEO_FFPROBE | ffprobe | FFprobe executable path |
| VIDEO_MODEL | ./models/yolov10n.onnx | Compatible detection model |
| VIDEO_DATA | ./data | Local session storage root |

Example for tools installed outside PATH:

```powershell
$env:VIDEO_FFMPEG = "C:\Tools\ffmpeg\bin\ffmpeg.exe"
$env:VIDEO_FFPROBE = "C:\Tools\ffmpeg\bin\ffprobe.exe"
$env:VIDEO_MODEL = "C:\Users\nisho\AI_Worspace\VideoAnalysis\models\yolov10n.onnx"
java --enable-native-access=ALL-UNNAMED -jar .\videoanalysis-app\target\videoanalysis-app-0.1.0-SNAPSHOT.jar
```

Use the direct JAR command for custom tool paths. The convenience launcher
checks commands on PATH. To change port, append `--server.port=8081` and open
http://localhost:8081. Do not expose the port through router forwarding.

## 8. Data and resource limits

- Maximum 250 MB/file, 30 minutes/video, 4096 pixels per dimension.
- Five uploaded videos and twenty jobs per running session.
- One active analysis. Decoding is bounded; model sessions and tensors close
  after use. At most 2,000 events/job, then a visible failure with partial results.
- Videos are stored under `data/session-<random>/`, not loaded wholly into RAM.
  Preview conversion needs additional disk space; allow several GB free.
- Rule definitions/results/reviews are in memory. Export JSON to retain them.
- Delete removes that uploaded copy, thumbnail, preview and related jobs.
- Normal shutdown removes the current media session. A forced shutdown/crash
  can leave orphaned `data/session-*` directories. After stopping all instances,
  delete those folders manually if they are no longer needed.
- Logs identify jobs and failures. Media/model files and generated data are
  excluded from Git. Do not commit personal camera footage to this repository.

## 9. Troubleshooting

| Symptom | What to do |
| --- | --- |
| `mvn` not recognised | Install Maven, add its bin to PATH, reopen PowerShell |
| Compiler missing | Install a JDK; confirm both java and javac work |
| No plugin for `spring-boot` | Use the JAR command or the module-specific command above |
| Model missing | Run setup-model.ps1 from this checkout; confirm the working directory |
| Invalid model shape | Use the verified model from models/README.md |
| FFmpeg missing | Check both tools on PATH or use VIDEO_FFMPEG / VIDEO_FFPROBE |
| MP4 cannot be read | Export MP4/H.264 again; do not merely rename a DAV file |
| Missing `libx264` encoder | Install an FFmpeg Windows build containing libx264 |
| Browser cannot connect | Check the Java console and exact port, and wait for startup |
| Port 8080 occupied | Start with --server.port=8081 and use that URL |
| Page says setup needed after installation | Refresh the page; verify the configured model path |
| Cancelled/failed job has findings | They are partial, clearly labelled by job state |
| Native library load error | Use a supported x64 JDK/OS and inspect ONNX runtime details in the console |
| UI unavailable from a phone | Expected for Phase 1: server listens only on the Windows loopback address |

## 10. Architecture, testing and roadmap

- [Architecture and design](docs/ARCHITECTURE.md)
- [API and JSON contract](docs/API.md)
- [Validation and limitations](docs/VALIDATION.md)
- [Model contract and licensing](models/README.md)

The GitHub Actions workflow builds on Linux/JDK 17 and Windows/JDK 25.
A separate Linux integration job decodes real video, runs the verified model,
and exercises upload, review, JSON export and range playback through HTTP.
Native tests skip locally when fixture environment variables are absent;
see VALIDATION.md to enable them. A green build does not validate accuracy on
night-time street footage; that needs representative camera clips.

Next phases: camera-specific accuracy evaluation; better tracking and activity
models; live DVR ingestion; evidence clip download; saved project sessions;
Android client and authenticated remote access. Broad anomaly detection and
feeding recognition require separate validation, not just another label.
