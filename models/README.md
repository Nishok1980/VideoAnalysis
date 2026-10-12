# Detection model

Run `scripts/setup-model.ps1` from the repository root on Windows.
The script downloads the 9.39 MB FP32 YOLOv10 nano COCO model from
https://huggingface.co/onnx-community/YOLOv10/blob/main/yolov10n.onnx
and verifies its published SHA-256:
`a77dd863933f184a19e84361c64b788228a7c7dacc2c78939239a96ad3efca3b`.

The model card labels the weights AGPL-3.0. Weights are not committed here.
Review this model's license before distributing a commercial application;
select suitably licensed weights/implementation for that product.
The adapter is replaceable through the `Detector` interface.

Expected input: float32 `[1,3,640,640]`, RGB, values 0..1, with 114 padding.
Expected first output: float32 `[1,N,6]` rows
`[x1, y1, x2, y2, confidence, COCO class id]` in padded-image pixels.
Class 0 is person; class 16 is dog. Other classes are ignored in Phase 1.
Do not substitute a YOLOv8, quantized, or raw-head export with another shape.

The model is opened per job and its SHA-256 is included in JSON exports.
Missing, unreadable or incompatible models fail visibly; there is no fake
or random detection fallback. This is an object detector, not a feeding
recognition model. A person close to a dog is only a review candidate.
