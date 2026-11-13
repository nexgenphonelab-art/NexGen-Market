const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const ts = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const SUPER_ADMIN_UID = "super_admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read users, products, or orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("products").where("isVisible", "==", true).get());
  await assertFails(unauthDb.collection("orders").where("customerId", "==", ALICE_UID).get());
});

test("Privilege escalation: customer cannot create user profile with admin role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "super_admin",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("User profile: owner can create customer profile and update allowed fields, but cannot change role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "customer",
      loyaltyPoints: 0,
      loyaltyTier: "Bronze",
      status: "active",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Allowed profile update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice M.",
      updatedAt: ts(),
    })
  );

  // Forbidden role escalation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: ts(),
    })
  );
});

test("Cross-user isolation: Bob cannot read Alice profile or cart", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "customer",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await adminDb.collection("users").doc(ALICE_UID).collection("cart").doc("item_1").set({
      userId: ALICE_UID,
      productId: "prod_1",
      shopId: "shop_1",
      shopOwnerId: BOB_UID,
      productName: "Phone",
      unitPrice: 100,
      quantity: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cart").get());
});

test("Universal shop & receipt config: owner can create shop and customize receipt, others cannot modify", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("shops").doc("shop_alice").set({
      ownerId: ALICE_UID,
      shopName: "Alice Electronics",
      currencyCode: "KES",
      isApproved: true,
      receiptHeaderTitle: "ALICE ELECTRONICS",
      receiptFooterMessage: "Thank you!",
      receiptPaperWidthMm: 80,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("shops").doc("shop_alice").update({
      receiptHeaderTitle: "ALICE TECH HUB",
      receiptPaperWidthMm: 58,
      updatedAt: ts(),
    })
  );

  await assertFails(
    bobDb.collection("shops").doc("shop_alice").update({
      receiptHeaderTitle: "HACKED",
      updatedAt: ts(),
    })
  );
});

test("Orders: customer cannot mark own order as Paid, shop owner can verify payment and update status", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("shops").doc("shop_bob").set({
      ownerId: BOB_UID,
      shopName: "Bob Gadgets",
      currencyCode: "KES",
      isApproved: true,
      receiptHeaderTitle: "BOB GADGETS",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("orders").doc("order_1").set({
      orderNumber: "NXG-10001",
      customerId: ALICE_UID,
      shopId: "shop_bob",
      shopOwnerId: BOB_UID,
      customerName: "Alice",
      totalAmount: 1500,
      paymentMethod: "M-Pesa STK",
      paymentStatus: "Unpaid",
      status: "Awaiting Payment",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Customer cannot self-mark Paid
  await assertFails(
    aliceDb.collection("orders").doc("order_1").update({
      paymentStatus: "Paid",
      status: "Paid",
      updatedAt: ts(),
    })
  );

  // Customer can submit payment reference for verification
  await assertSucceeds(
    aliceDb.collection("orders").doc("order_1").update({
      paymentReference: "SJK98P12QA",
      paymentStatus: "Pending Verification",
      updatedAt: ts(),
    })
  );

  // Shop owner can verify and mark Paid + Shipped
  await assertSucceeds(
    bobDb.collection("orders").doc("order_1").update({
      paymentStatus: "Paid",
      status: "Shipped",
      updatedAt: ts(),
    })
  );

  // Unfiltered list query fails
  await assertFails(aliceDb.collection("orders").get());

  // Filtered list query succeeds
  await assertSucceeds(aliceDb.collection("orders").where("customerId", "==", ALICE_UID).get());
});

test("Super Admin bootstrap via verified email works for admin management", async () => {
  const superDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: "nexgenphonelab@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    superDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice Admin",
      role: "admin",
      permissions: ["ORDERS", "PRODUCTS"],
      isActive: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Spoofed unverified email must fail
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "nexgenphonelab@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("admins").doc("spoof_uid").set({
      uid: "spoof_uid",
      email: "nexgenphonelab@gmail.com",
      displayName: "Spoofer",
      role: "super_admin",
      isActive: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("Restock alerts: customer can subscribe & view own alert, shop owner can mark notified, third-party denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();

  await assertSucceeds(
    aliceDb.collection("restock_alerts").doc(`${ALICE_UID}_prod_1`).set({
      userId: ALICE_UID,
      customerName: "Alice",
      productId: "prod_1",
      productName: "Apex Phone",
      shopId: "shop_bob",
      shopOwnerId: BOB_UID,
      status: "waiting",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Third party cannot read or hijack
  await assertFails(charlieDb.collection("restock_alerts").doc(`${ALICE_UID}_prod_1`).get());

  // Shop owner can update status to notified
  await assertSucceeds(
    bobDb.collection("restock_alerts").doc(`${ALICE_UID}_prod_1`).update({
      status: "notified",
      updatedAt: ts(),
    })
  );
});

test("Shop assets & Gateway config: strictly private to shop owner / admin, customers denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    bobDb.collection("shop_assets").doc("asset_1").set({
      shopId: "shop_bob",
      shopOwnerId: BOB_UID,
      name: "POS Thermal Printer + PC",
      category: "Electronics/POS",
      quantity: 2,
      purchasePrice: 45000,
      currentValue: 40000,
      condition: "Good",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertFails(aliceDb.collection("shop_assets").doc("asset_1").get());

  await assertSucceeds(
    bobDb.collection("shops").doc("shop_bob").collection("gateway_config").doc("default").set({
      shopOwnerId: BOB_UID,
      darajaEnvironment: "sandbox",
      darajaShortcode: "174379",
      darajaConsumerKey: "secret_key",
      darajaConsumerSecret: "secret_val",
      darajaPasskey: "pass_val",
      stkEnabled: true,
      updatedAt: ts(),
    })
  );
  // Customer cannot read M-Pesa Daraja secrets
  await assertFails(
    aliceDb.collection("shops").doc("shop_bob").collection("gateway_config").doc("default").get()
  );
});
