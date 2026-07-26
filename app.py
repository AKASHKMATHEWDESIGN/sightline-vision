"""
Sightline Vision — Backend (Automatic Detection)

Given an uploaded image, this service:
  1. Detects every face in the image using OpenCV's DNN face detector
     (SSD/ResNet-10 — small, fast, CPU-friendly).
  2. For each face, locates 68 facial landmarks using OpenCV's LBF
     facemark model.
  3. Estimates head pose (yaw/pitch) via solvePnP against a generic
     3D face model, then projects a "facing direction" ray forward
     from the eye midpoint.
  4. Extends that ray to the image boundary and computes 10 equidistant
     target points along it — exactly as before, just automatically
     per detected person instead of per manual tap.

This is a head-orientation estimate (a common proxy used in broadcast
"gaze cone" graphics), not true iris-tracking eye-gaze. Accuracy drops
for small, blurry, or heavily turned/occluded faces — that is an
inherent limit of monocular, landmark-based pose estimation, not a bug.
"""

import os
import traceback
from typing import List, Dict, Optional

import numpy as np
import cv2
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS

GRID = 1000.0
POINTS_PER_LINE = 10
MAX_FACES = 24  # sane upper bound so huge crowd shots don't blow up the table
MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")

FACE_PROTO = os.path.join(MODEL_DIR, "deploy.prototxt")
FACE_MODEL = os.path.join(MODEL_DIR, "res10_300x300_ssd_iter_140000.caffemodel")
LANDMARK_MODEL = os.path.join(MODEL_DIR, "lbfmodel.yaml")

app = Flask(__name__, static_folder=".", static_url_path="")
CORS(app, resources={r"/api/*": {"origins": "*"}})

_face_net = None
_facemark = None
_load_error: Optional[str] = None


def _load_models():
    global _face_net, _facemark, _load_error
    try:
        if not (os.path.exists(FACE_PROTO) and os.path.exists(FACE_MODEL)):
            raise FileNotFoundError(
                "Face detector model files are missing from /app/models. "
                "Check the Dockerfile's model download step."
            )
        _face_net = cv2.dnn.readNetFromCaffe(FACE_PROTO, FACE_MODEL)

        if hasattr(cv2, "face"):
            if not os.path.exists(LANDMARK_MODEL):
                raise FileNotFoundError(
                    "Landmark model (lbfmodel.yaml) is missing from /app/models."
                )
            _facemark = cv2.face.createFacemarkLBF()
            _facemark.loadModel(LANDMARK_MODEL)
        else:
            raise RuntimeError(
                "opencv-contrib (cv2.face) is not available — install "
                "opencv-contrib-python-headless instead of opencv-python-headless."
            )
    except Exception as exc:
        _load_error = str(exc)
        traceback.print_exc()


_load_models()

MODEL_POINTS_3D = np.array(
    [
        (0.0, 0.0, 0.0),
        (0.0, -330.0, -65.0),
        (-225.0, 170.0, -135.0),
        (225.0, 170.0, -135.0),
        (-150.0, -150.0, -125.0),
        (150.0, -150.0, -125.0),
    ],
    dtype=np.float64,
)
LANDMARK_IDX = {"nose": 30, "chin": 8, "eye_l": 36, "eye_r": 45, "mouth_l": 48, "mouth_r": 54}

PALETTE = [
    "#00ff87",
    "#00e0ff",
    "#ff2ec4",
    "#ffb020",
    "#c6ff00",
    "#a259ff",
    "#ff5252",
    "#00ffd0",
]


def _clamp(v: float, lo: float = 0.0, hi: float = GRID) -> float:
    return float(np.clip(v, lo, hi))


def _detect_faces(image: np.ndarray) -> List[Dict]:
    h, w = image.shape[:2]
    blob = cv2.dnn.blobFromImage(
        cv2.resize(image, (300, 300)), 1.0, (300, 300), (104.0, 177.0, 123.0)
    )
    _face_net.setInput(blob)
    detections = _face_net.forward()

    faces = []
    for i in range(detections.shape[2]):
        confidence = float(detections[0, 0, i, 2])
        if confidence < 0.5:
            continue
        box = detections[0, 0, i, 3:7] * np.array([w, h, w, h])
        x1, y1, x2, y2 = box.astype(int)
        x1, y1 = max(0, x1), max(0, y1)
        x2, y2 = min(w, x2), min(h, y2)
        if x2 <= x1 or y2 <= y1:
            continue
        faces.append({"box": (x1, y1, x2 - x1, y2 - y1), "confidence": confidence})

    faces.sort(key=lambda f: f["confidence"] * (f["box"][2] * f["box"][3]), reverse=True)
    return faces[:MAX_FACES]


def _estimate_gaze_ray(image_shape, landmarks_2d: np.ndarray):
    h, w = image_shape[:2]
    image_points = np.array(
        [
            landmarks_2d[LANDMARK_IDX["nose"]],
            landmarks_2d[LANDMARK_IDX["chin"]],
            landmarks_2d[LANDMARK_IDX["eye_l"]],
            landmarks_2d[LANDMARK_IDX["eye_r"]],
            landmarks_2d[LANDMARK_IDX["mouth_l"]],
            landmarks_2d[LANDMARK_IDX["mouth_r"]],
        ],
        dtype=np.float64,
    )

    focal_length = w
    center = (w / 2.0, h / 2.0)
    camera_matrix = np.array(
        [[focal_length, 0, center[0]], [0, focal_length, center[1]], [0, 0, 1]],
        dtype=np.float64,
    )
    dist_coeffs = np.zeros((4, 1))

    success, rvec, tvec = cv2.solvePnP(
        MODEL_POINTS_3D, image_points, camera_matrix, dist_coeffs,
        flags=cv2.SOLVEPNP_ITERATIVE,
    )
    if not success:
        return None, None

    eye_mid = (landmarks_2d[LANDMARK_IDX["eye_l"]] + landmarks_2d[LANDMARK_IDX["eye_r"]]) / 2.0

    forward_3d = np.array([[0.0, 0.0, 1000.0]])
    forward_2d, _ = cv2.projectPoints(forward_3d, rvec, tvec, camera_matrix, dist_coeffs)
    forward_2d = forward_2d.reshape(-1, 2)[0]

    direction = forward_2d - eye_mid
    norm = np.linalg.norm(direction)
    if norm < 1e-6:
        return eye_mid, None
    return eye_mid, direction / norm


def _ray_boundary_intersection(origin: np.ndarray, direction: np.ndarray) -> np.ndarray:
    t_candidates = []
    for axis in (0, 1):
        d = direction[axis]
        if abs(d) < 1e-12:
            continue
        for bound in (0.0, GRID):
            t = (bound - origin[axis]) / d
            if t > 1e-6:
                point = origin + t * direction
                if -1e-6 <= point[0] <= GRID + 1e-6 and -1e-6 <= point[1] <= GRID + 1e-6:
                    t_candidates.append(t)
    if not t_candidates:
        return origin.copy()
    t_min = min(t_candidates)
    exit_point = origin + t_min * direction
    exit_point[0] = _clamp(exit_point[0])
    exit_point[1] = _clamp(exit_point[1])
    return exit_point


def _build_vector_result(origin_norm, direction, id_offset, color, confidence) -> Dict:
    end = _ray_boundary_intersection(origin_norm, direction)
    targets = []
    for i in range(1, POINTS_PER_LINE + 1):
        t = i / POINTS_PER_LINE
        point = origin_norm + t * (end - origin_norm)
        targets.append({
            "id": id_offset + i,
            "x": round(_clamp(point[0]), 2),
            "y": round(_clamp(point[1]), 2),
        })
    return {
        "start": {"x": round(float(origin_norm[0]), 2), "y": round(float(origin_norm[1]), 2)},
        "end": {"x": round(float(end[0]), 2), "y": round(float(end[1]), 2)},
        "targets": targets,
        "color": color,
        "confidence": round(confidence, 3),
    }


@app.route("/")
def index():
    return send_from_directory(".", "index.html")


@app.route("/api/health")
def health():
    return jsonify({
        "status": "ok" if _load_error is None else "degraded",
        "error": _load_error,
    })


@app.route("/api/detect", methods=["POST"])
def detect():
    if _load_error is not None:
        return jsonify({"error": f"Detection models unavailable: {_load_error}"}), 503

    if "image" not in request.files:
        return jsonify({"error": "No image file uploaded under field name 'image'."}), 400

    file = request.files["image"]
    file_bytes = np.frombuffer(file.read(), dtype=np.uint8)
    image = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
    if image is None:
        return jsonify({"error": "Could not decode the uploaded file as an image."}), 400

    h, w = image.shape[:2]

    try:
        faces = _detect_faces(image)
    except Exception as exc:
        traceback.print_exc()
        return jsonify({"error": f"Face detection failed: {exc}"}), 500

    if not faces:
        return jsonify({"grid": {"min": 0, "max": GRID}, "image": {"width": w, "height": h}, "players": []})

    boxes = np.array([f["box"] for f in faces], dtype=np.int32)
    try:
        ok, landmarks_all = _facemark.fit(image, boxes)
    except Exception as exc:
        traceback.print_exc()
        return jsonify({"error": f"Landmark detection failed: {exc}"}), 500

    players = []
    id_offset = 0
    for i, face in enumerate(faces):
        if not ok or i >= len(landmarks_all):
            continue
        landmarks_2d = landmarks_all[i][0]
        origin_px, direction = _estimate_gaze_ray(image.shape, landmarks_2d)
        if origin_px is None or direction is None:
            continue

        origin_norm = np.array([origin_px[0] / w * GRID, origin_px[1] / h * GRID])
        color = PALETTE[i % len(PALETTE)]
        vec = _build_vector_result(origin_norm, direction, id_offset, color, face["confidence"])
        vec["face_box_norm"] = {
            "x": round(face["box"][0] / w * GRID, 2),
            "y": round(face["box"][1] / h * GRID, 2),
            "w": round(face["box"][2] / w * GRID, 2),
            "h": round(face["box"][3] / h * GRID, 2),
        }
        players.append(vec)
        id_offset += POINTS_PER_LINE

    return jsonify({
        "grid": {"min": 0, "max": GRID},
        "image": {"width": w, "height": h},
        "players": players,
    })


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
