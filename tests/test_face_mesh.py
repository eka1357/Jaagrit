import cv2
import mediapipe as mp
import time
import math

mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(
    max_num_faces=1,
    refine_landmarks=True,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)

RIGHT_EYE = [33, 160, 158, 133, 153, 144]
LEFT_EYE = [362, 385, 387, 263, 373, 380]


def eye_aspect_ratio(landmarks, eye_indices):
    pts = [(landmarks[i].x, landmarks[i].y) for i in eye_indices]
    v1 = math.dist(pts[1], pts[5])
    v2 = math.dist(pts[2], pts[4])
    h = math.dist(pts[0], pts[3])
    if h == 0:
        return 0.0
    return (v1 + v2) / (2.0 * h)


cap = cv2.VideoCapture(0)
inference_times = []
ear_open_samples = []
ear_closed_samples = []
mode = "OPEN"
phase_start = time.time()

print("=" * 50)
print("PHASE 1: Look at camera with eyes OPEN for 5 sec")
print("=" * 50)

while cap.isOpened():
    t0 = time.time()
    ret, frame = cap.read()
    if not ret:
        break

    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    infer_start = time.time()
    results = face_mesh.process(rgb)
    infer_time = (time.time() - infer_start) * 1000  # ms
    inference_times.append(infer_time)

    elapsed = time.time() - phase_start

    if results.multi_face_landmarks:
        lm = results.multi_face_landmarks[0].landmark
        ear_r = eye_aspect_ratio(lm, RIGHT_EYE)
        ear_l = eye_aspect_ratio(lm, LEFT_EYE)
        ear = (ear_r + ear_l) / 2.0

        if mode == "OPEN":
            ear_open_samples.append(ear)
            status = f"OPEN EAR: {ear:.3f}"
        elif mode == "CLOSED":
            ear_closed_samples.append(ear)
            status = f"CLOSED EAR: {ear:.3f}"
        else:
            status = f"EAR: {ear:.3f}"

        cv2.putText(frame, status, (10, 30),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
        cv2.putText(frame, "FACE FOUND", (10, 60),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
    else:
        cv2.putText(frame, "NO FACE", (10, 30),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2)

    cv2.putText(frame, f"Inference: {infer_time:.0f}ms", (10, 90),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 0), 2)
    cv2.putText(frame, f"Mode: {mode} ({elapsed:.1f}s)", (10, 120),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 0), 2)

    cv2.imshow("Face Mesh Test", frame)

    # Phase transitions — elif chain so only one fires per iteration
    if mode == "OPEN" and elapsed >= 5:
        mode = "CLOSED"
        phase_start = time.time()  # Reset timer for new phase
        print("\n" + "=" * 50)
        print("PHASE 2: CLOSE your eyes for 5 sec")
        print("=" * 50)
    elif mode == "CLOSED" and elapsed >= 5:
        mode = "DONE"
        phase_start = time.time()
        print("\n" + "=" * 50)
        print("PHASE 3: Try glasses, dim light, looking away")
        print("Press 'q' when done observing")
        print("=" * 50)

    if cv2.waitKey(1) & 0xFF == ord("q"):
        break

cap.release()
cv2.destroyAllWindows()

# Report
print("\n" + "=" * 50)
print("RESULTS")
print("=" * 50)

if ear_open_samples:
    avg_open = sum(ear_open_samples) / len(ear_open_samples)
    print(f"Your OPEN eye EAR:   {avg_open:.3f}  ({len(ear_open_samples)} samples)")
else:
    print("ERROR: No open-eye samples captured")

if ear_closed_samples:
    avg_closed = sum(ear_closed_samples) / len(ear_closed_samples)
    print(f"Your CLOSED eye EAR: {avg_closed:.3f}  ({len(ear_closed_samples)} samples)")
else:
    print("ERROR: No closed-eye samples captured — did you close your eyes?")

if ear_open_samples and ear_closed_samples:
    threshold = (avg_open + avg_closed) / 2
    gap = avg_open - avg_closed
    print(f"Suggested threshold: {threshold:.3f}")
    print(f"Gap (open - closed):  {gap:.3f}")
    if gap > 0.05:
        print("PASS: Clear separation between open and closed")
    else:
        print("WARNING: Small gap. May need iris-based detection instead")

if inference_times:
    avg_ms = sum(inference_times) / len(inference_times)
    print(f"\nMediaPipe inference time: {avg_ms:.0f} ms/frame (laptop CPU)")
    print(f"  Note: This is laptop CPU speed, NOT phone NPU speed.")
    print(f"  The iQOO 15 NPU will be different — measure during hour 0-2.")

print("\nManual checks (observe during PHASE 3):")
print("- Glasses on:     face tracked? EAR changed?")
print("- Dim room:       face tracked? (try screen at max brightness)")
print("- Look down 2s:   face lost or just head pose change?")
print("- Turn head 45°:  at what angle is face lost?")
