const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildDraftPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    workspaceId: "ws_g04",
    grade: "G04",
    bookKey: "ARAB",
    unitKey: "unit_01",
    lessonKey: "lesson_01",
    task: "explanation",
    title: "الدرس الأول",
    provider: "Gemini",
    model: "gemini-2.5-flash",
    content: "شرح الدرس الأول بالتفصيل.",
    status: "DRAFT",
    version: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildProgressPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    workspaceId: "ws_g04",
    bookKey: "ARAB",
    unitKey: "unit_01",
    lessonKey: "lesson_01",
    lessonTitle: "الدرس الأول",
    completed: true,
    xpEarned: 45,
    scorePercent: 90,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user cannot read or write drafts or progress", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/drafts`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/drafts`).doc("draft_1").set(buildDraftPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/progress`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/progress`).doc("prog_1").set(buildProgressPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid Kotlin draft CRUD, partial update, and @DocumentId exclusion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const draftsRef = aliceDb.collection(`users/${ALICE_UID}/drafts`);

  await assertSucceeds(draftsRef.doc("draft_1").set(buildDraftPayload(ALICE_UID)));
  await assertSucceeds(draftsRef.get());
  await assertSucceeds(draftsRef.doc("draft_1").get());

  // Partial status update (approve)
  await assertSucceeds(
    draftsRef.doc("draft_1").update({
      status: "APPROVED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Content edit update
  await assertSucceeds(
    draftsRef.doc("draft_1").update({
      title: "الدرس الأول - محدث",
      content: "محتوى محدث",
      status: "DRAFT",
      version: 2,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    draftsRef.doc("draft_with_id").set(buildDraftPayload(ALICE_UID, { id: "draft_with_id" }))
  );

  // Reject ghost field
  await assertFails(
    draftsRef.doc("draft_ghost").set(buildDraftPayload(ALICE_UID, { isVerified: true }))
  );

  // Reject immutable createdAt mutation
  await assertFails(
    draftsRef.doc("draft_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Owner delete
  await assertSucceeds(draftsRef.doc("draft_1").delete());
});

test("Authenticated owner: valid LessonProgress CRUD with optional notes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const progressRef = aliceDb.collection(`users/${ALICE_UID}/progress`);

  await assertSucceeds(progressRef.doc("prog_1").set(buildProgressPayload(ALICE_UID)));

  // Update with optional notes
  await assertSucceeds(
    progressRef.doc("prog_1").update({
      completed: true,
      xpEarned: 60,
      scorePercent: 100,
      notes: "ملاحظة الطالب",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject explicit null optional field
  await assertFails(
    progressRef.doc("prog_null").set(buildProgressPayload(ALICE_UID, { notes: null }))
  );

  // Reject score > 100
  await assertFails(
    progressRef.doc("prog_bad_score").set(buildProgressPayload(ALICE_UID, { scorePercent: 150 }))
  );

  await assertSucceeds(progressRef.get());
});

test("Temporal integrity: reject future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const future = new Date(Date.now() + 60000);

  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/drafts`)
      .doc("draft_future")
      .set(buildDraftPayload(ALICE_UID, { createdAt: future, updatedAt: future }))
  );
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/progress`)
      .doc("prog_future")
      .set(buildProgressPayload(ALICE_UID, { createdAt: future, updatedAt: future }))
  );
});

test("Cross-user isolation: Alice cannot read, list, create, update, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection(`users/${BOB_UID}/drafts`).doc("bob_draft").set(buildDraftPayload(BOB_UID));
    await adminDb.collection(`users/${BOB_UID}/progress`).doc("bob_prog").set(buildProgressPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(aliceDb.collection(`users/${BOB_UID}/drafts`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/drafts`).doc("bob_draft").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/drafts`).doc("hijack").set(buildDraftPayload(ALICE_UID))
  );
  await assertFails(
    aliceDb
      .collection(`users/${BOB_UID}/drafts`)
      .doc("bob_draft")
      .update({ status: "APPROVED", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/drafts`).doc("bob_draft").delete());

  await assertFails(aliceDb.collection(`users/${BOB_UID}/progress`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/progress`).doc("bob_prog").get());
  await assertFails(
    aliceDb
      .collection(`users/${BOB_UID}/progress`)
      .doc("bob_prog")
      .update({ completed: false, xpEarned: 0, scorePercent: 0, updatedAt: new Date(Date.now() - 500) })
  );
});
