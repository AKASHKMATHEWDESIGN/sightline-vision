"""
Sightline Vision — Backend
Flask + NumPy service that takes two player gaze definitions (eye origin +
aim point, on a normalized 0-1000 x 0-1000 grid) and returns:

  - the exact Start Coordinate (eye origin)
  - the exact End Coordinate (where the ray exits the 0-1000 canvas boundary)
  - 10 equidistant target points along that segment, per player

All math is done with NumPy so it is trivially extensible (e.g. to add
perspective correction later) and runs fast even on small free-tier
containers.
"""

import os
from typing import Tuple, List, Dict

import numpy as np
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS

GRID_MIN = 0.0
GRID_MAX = 1000.0
POINTS_PER_LINE = 10

app = Flask(__name__, static_folder=".", static_url_path="")

# Allow the frontend to be hosted anywhere (same-origin on Render/Railway,
# or a separate static host) and still call this API.
CORS(app, resources={r"/api/*": {"origins": "*"}})


class VectorError(ValueError):
    pass


def _clamp(value: float, lo: float = GRID_MIN, hi: float = GRID_MAX) -> float:
    return float(np.clip(value, lo, hi))


def _validate_point(point) -> np.ndarray:
    if (
        not isinstance(point, (list, tuple))
        or len(point) != 2
        or not all(isinstance(v, (int, float)) for v in point)
    ):
        raise VectorError("Each point must be a [x, y] pair of numbers.")
    x, y = point
    return np.array([_clamp(x), _clamp(y)], dtype=np.float64)


def _ray_boundary_intersection(origin: np.ndarray, direction: np.ndarray) -> np.ndarray:
    """
    Given an origin inside (or on) the [0,1000]x[0,1000] square and a unit
    direction vector, find the point where the ray exits the square.
    Uses the classic slab method, restricted to t > 1e-6 (forward only).
    """
    t_candidates = []

    for axis in (0, 1):
        d = direction[axis]
        if abs(d) < 1e-12:
            continue
        for bound in (GRID_MIN, GRID_MAX):
            t = (bound - origin[axis]) / d
            if t > 1e-6:
                point = origin + t * direction
                # Confirm the intersection is actually within the square
                # (within a small epsilon) on both axes.
                if (
                    -1e-6 <= point[0] <= GRID_MAX + 1e-6
                    and -1e-6 <= point[1] <= GRID_MAX + 1e-6
                ):
                    t_candidates.append(t)

    if not t_candidates:
        # Degenerate case (origin == aim point, or origin already on
        # boundary facing outward). Fall back to the origin itself.
        return origin.copy()

    t_min = min(t_candidates)
    exit_point = origin + t_min * direction
    exit_point[0] = _clamp(exit_point[0])
    exit_point[1] = _clamp(exit_point[1])
    return exit_point


def _compute_vector(origin_raw, aim_raw, id_offset: int) -> Dict:
    origin = _validate_point(origin_raw)
    aim = _validate_point(aim_raw)

    raw_direction = aim - origin
    norm = np.linalg.norm(raw_direction)
    if norm < 1e-9:
        raise VectorError(
            "Eye origin and aim point are identical — cannot determine a gaze direction."
        )
    direction = raw_direction / norm

    end = _ray_boundary_intersection(origin, direction)

    targets: List[Dict] = []
    for i in range(1, POINTS_PER_LINE + 1):
        t = i / POINTS_PER_LINE
        point = origin + t * (end - origin)
        targets.append(
            {
                "id": id_offset + i,
                "x": round(_clamp(point[0]), 2),
                "y": round(_clamp(point[1]), 2),
            }
        )

    return {
        "start": {"x": round(float(origin[0]), 2), "y": round(float(origin[1]), 2)},
        "end": {"x": round(float(end[0]), 2), "y": round(float(end[1]), 2)},
        "targets": targets,
    }


@app.route("/")
def index():
    return send_from_directory(".", "index.html")


@app.route("/api/health")
def health():
    return jsonify({"status": "ok"})


@app.route("/api/compute", methods=["POST"])
def compute():
    payload = request.get_json(silent=True)
    if not payload:
        return jsonify({"error": "Missing or invalid JSON body."}), 400

    try:
        player1_raw = payload.get("player1")
        player2_raw = payload.get("player2")
        if not player1_raw or not player2_raw:
            raise VectorError("Both player1 and player2 vectors are required.")

        player1 = _compute_vector(
            player1_raw.get("origin"), player1_raw.get("aim"), id_offset=0
        )
        player2 = _compute_vector(
            player2_raw.get("origin"), player2_raw.get("aim"), id_offset=10
        )

        return jsonify(
            {
                "grid": {"min": GRID_MIN, "max": GRID_MAX},
                "player1": player1,
                "player2": player2,
            }
        )

    except VectorError as exc:
        return jsonify({"error": str(exc)}), 422
    except Exception as exc:  # pragma: no cover - defensive catch-all
        return jsonify({"error": f"Unexpected server error: {exc}"}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
