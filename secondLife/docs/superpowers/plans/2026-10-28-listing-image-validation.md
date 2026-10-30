# Listing Image Validation Implementation Plan

> **For agentic workers:** Execute inline with test-driven-development; review the complete diff before delivery.

**Goal:** Implement the image rules approved in the conversation for listing uploads and provide reproducible Swagger test files.

**Architecture:** Keep POST /api/v1/posts/init and its response unchanged. A shared image helper detects actual JPEG/PNG/WebP, bounds decoding, normalizes orientation and strips metadata before storage. Validate the complete batch before Cloudinary, persistence or AI calls. Generic media uploads reuse safe file checks without imposing listing photo counts or the 600px listing minimum on avatars/KYC.

**Tech Stack:** Java 21, existing Spring Boot/Web, ImageIO and installed TwelveMonkeys WebP reader, JUnit/Mockito, Cloudinary SDK. No new dependency or database migration.

**Spec:** User-approved image rules in this conversation: 3–6 listing images; 10 MiB each; short side >=600; <=16000px per side and <=25000000 pixels; static JPEG/PNG/WebP; reject invalid/duplicate files; orient then remove metadata; controlled storage names; per-image errors and HTTP 413 for oversized multipart.

## Global Constraints

- Follow docs/BACKEND_CODING_STANDARDS.md; no unrelated FE edits, auth changes, commits or deployments.
- Existing environment values remain authoritative; new minimum/normalized-side settings have safe defaults (600/2560).
- Unclear/blurred photos and cross-listing similarity remain review signals, not new hard blocks. HEIC conversion belongs on clients; this BE rejects unsupported input explicitly.
- Cleanup is best effort on new assets created by a failed operation; retain valid saved drafts on later AI/provider failures.
- Use an isolated source snapshot without .env for tests; synchronize only reviewed changed files after checking their original hashes.

## Review Focus

- Genuine WebP, APNG/WebP animation, forged MIME/extension and truncated image bytes.
- Dimension/pixel rejection before decoding a large bitmap; exact byte/minimum boundaries.
- All EXIF orientations and GPS metadata removal, with no accidental rotate twice or transparency loss.
- Invalid image late in a batch must cause zero Cloudinary, draft save and credit side effects.
- Upload/DB failure must clean only newly created assets; cleanup failure must not hide the original exception.

## Tasks

### 1. Regression and shared validation
- [x] Write and run failing tests against initPost for corrupt, undersized and duplicate images.
- [x] Implement bounded image preparation and aggregate per-index errors; add parameterized actual-image tests for boundaries, formats, metadata and animation.
- [x] Integrate listing preparation and generic media validation; replace fake successful image test fixtures with real images.

### 2. Storage and error contract
- [x] Test and implement controlled Cloudinary IDs/resource type and best-effort cleanup for partial uploads and draft persistence failure.
- [x] Test and implement global 400/413 image and multipart responses in the existing ErrorResponse shape.

### 3. Manual test kit and verification
- [x] Generate small real JPEG/PNG/WebP fixtures plus invalid/oversized/rotated/animated variants and a manifest.
- [x] Write Vietnamese Swagger test steps, exact request fields, expected HTTP/errors and side-effect checks; document web/mobile integration.
- [x] Run focused tests, full suite and package build; record baseline failures/skips distinctly.
- [x] Review all requirements/diff and prepare guarded synchronization; Apply-ImageValidation.ps1 validates baseline/new hashes and backs up overwritten files.
