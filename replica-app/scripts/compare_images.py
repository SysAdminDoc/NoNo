"""Create deterministic visual-comparison artifacts for two Android screenshots."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageEnhance, ImageOps


def load_mask(path: Path | None, size: tuple[int, int]) -> np.ndarray:
    mask = np.ones((size[1], size[0]), dtype=bool)
    if path is None:
        return mask
    rectangles = json.loads(path.read_text(encoding="utf-8"))
    for item in rectangles:
        x = max(0, int(item["x"]))
        y = max(0, int(item["y"]))
        right = min(size[0], x + int(item["width"]))
        bottom = min(size[1], y + int(item["height"]))
        mask[y:bottom, x:right] = False
    return mask


def gaussian_blur(image: np.ndarray, radius: int = 5, sigma: float = 1.5) -> np.ndarray:
    """Apply a small separable Gaussian blur without adding a SciPy dependency."""
    axis = np.arange(-radius, radius + 1, dtype=np.float32)
    kernel = np.exp(-(axis * axis) / (2.0 * sigma * sigma))
    kernel /= kernel.sum()
    padded_x = np.pad(image, ((0, 0), (radius, radius)), mode="reflect")
    horizontal = np.empty_like(image, dtype=np.float32)
    for row in range(image.shape[0]):
        horizontal[row] = np.convolve(padded_x[row], kernel, mode="valid")
    padded_y = np.pad(horizontal, ((radius, radius), (0, 0)), mode="reflect")
    blurred = np.empty_like(image, dtype=np.float32)
    for column in range(image.shape[1]):
        blurred[:, column] = np.convolve(padded_y[:, column], kernel, mode="valid")
    return blurred


def windowed_ssim(base: np.ndarray, current: np.ndarray, mask: np.ndarray) -> float:
    """Return Gaussian-windowed SSIM; this is diagnostic, not the pass/fail gate."""
    base = base.astype(np.float32)
    current = current.astype(np.float32)
    mu_base = gaussian_blur(base)
    mu_current = gaussian_blur(current)
    sigma_base = np.maximum(gaussian_blur(base * base) - mu_base * mu_base, 0.0)
    sigma_current = np.maximum(gaussian_blur(current * current) - mu_current * mu_current, 0.0)
    covariance = gaussian_blur(base * current) - mu_base * mu_current
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    numerator = (2 * mu_base * mu_current + c1) * (2 * covariance + c2)
    denominator = (mu_base * mu_base + mu_current * mu_current + c1) * (sigma_base + sigma_current + c2)
    values = (numerator / denominator)[mask]
    return float(values.mean()) if values.size else 1.0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--current", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--screen-id", required=True)
    # No default: the authoritative value lives in validation/screen-validation-matrix.csv.
    # A default here silently disagreed with it (0.90 vs 0.85) for anyone invoking the
    # script directly.
    parser.add_argument("--threshold", type=float, required=True)
    parser.add_argument("--mask", type=Path)
    args = parser.parse_args()

    baseline = Image.open(args.baseline).convert("RGB")
    current = Image.open(args.current).convert("RGB")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    dimensions_match = baseline.size == current.size

    if not dimensions_match:
        metrics = {
            "screen_id": args.screen_id,
            "baseline": str(args.baseline.resolve()),
            "current": str(args.current.resolve()),
            "baseline_dimensions": list(baseline.size),
            "current_dimensions": list(current.size),
            "dimensions_match": False,
            "threshold": args.threshold,
            "result": "FAIL_DIMENSIONS",
        }
        (args.output_dir / f"{args.screen_id}-metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
        return 2

    mask = load_mask(args.mask, baseline.size)
    base_array = np.asarray(baseline, dtype=np.float32)
    current_array = np.asarray(current, dtype=np.float32)
    selected = np.repeat(mask[:, :, None], 3, axis=2)
    delta = np.abs(base_array - current_array)
    values = delta[selected]
    mae = float(values.mean()) if values.size else 0.0
    rmse = float(np.sqrt(np.mean(np.square(values)))) if values.size else 0.0
    pixel_similarity = float(max(0.0, 1.0 - mae / 255.0))

    structural_similarity = windowed_ssim(
        np.asarray(ImageOps.grayscale(baseline), dtype=np.float32),
        np.asarray(ImageOps.grayscale(current), dtype=np.float32),
        mask,
    )

    side = Image.new("RGB", (baseline.width * 2, baseline.height), "black")
    side.paste(baseline, (0, 0))
    side.paste(current, (baseline.width, 0))
    side.save(args.output_dir / f"{args.screen_id}-side-by-side.png")
    Image.blend(baseline, current, 0.5).save(args.output_dir / f"{args.screen_id}-overlay-50.png")

    raw_diff = ImageChops.difference(baseline, current)
    raw_diff.save(args.output_dir / f"{args.screen_id}-diff.png")
    enhanced = ImageEnhance.Contrast(raw_diff).enhance(3.0)
    enhanced_array = np.asarray(enhanced, dtype=np.uint8)
    intensity = enhanced_array.max(axis=2)
    heat = np.zeros_like(enhanced_array)
    heat[:, :, 0] = intensity
    heat[:, :, 1] = (intensity.astype(np.float32) * 0.22).astype(np.uint8)
    heat[:, :, 2] = (255 - intensity) // 10
    heat[~mask] = np.array([30, 30, 30], dtype=np.uint8)
    Image.fromarray(heat, "RGB").save(args.output_dir / f"{args.screen_id}-heatmap.png")

    result = "PASS" if pixel_similarity >= args.threshold else "FAIL_THRESHOLD"
    metrics = {
        "screen_id": args.screen_id,
        "baseline": str(args.baseline.resolve()),
        "current": str(args.current.resolve()),
        "mask": str(args.mask.resolve()) if args.mask else None,
        "baseline_dimensions": list(baseline.size),
        "current_dimensions": list(current.size),
        "dimensions_match": True,
        "compared_pixel_ratio": float(mask.mean()),
        "mean_absolute_error": round(mae, 6),
        "root_mean_square_error": round(rmse, 6),
        "pixel_similarity": round(pixel_similarity, 8),
        "windowed_structural_similarity": round(structural_similarity, 8),
        "threshold": args.threshold,
        "result": result,
    }
    (args.output_dir / f"{args.screen_id}-metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    return 0 if result == "PASS" else 3


if __name__ == "__main__":
    raise SystemExit(main())
