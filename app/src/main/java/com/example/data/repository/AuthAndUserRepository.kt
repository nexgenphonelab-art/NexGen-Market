package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AdminRecord
import com.example.data.model.AppNotification
import com.example.data.model.SavedAddress
import com.example.data.model.StaffMember
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

class AuthAndUserRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(
        uid: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        isEmailVerified: Boolean
    ): Result<UserProfile> = runCatching {
        val docRef = db.collection("users").document(uid)
        val existingSnap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        if (existingSnap.exists()) {
            existingSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(userId = uid, displayName = displayName, email = email)
        } else {
            val isSuperAdminEmail = isSuperAdminEmail(email, isEmailVerified)
            val assignedRole = if (isSuperAdminEmail) "super_admin" else "customer"
            val payload = hashMapOf<String, Any>(
                "userId" to uid,
                "displayName" to displayName.ifBlank { "NexGen Shopper" },
                "role" to assignedRole,
                "loyaltyPoints" to 100L,
                "loyaltyTier" to "Bronze",
                "customerGroup" to "Retail",
                "totalSpent" to 0.0,
                "orderCount" to 0L,
                "outstandingBalance" to 0.0,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (!email.isNullOrBlank()) payload["email"] = email
            if (!photoUrl.isNullOrBlank()) payload["photoUrl"] = photoUrl

            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }

            UserProfile(
                id = uid,
                userId = uid,
                displayName = displayName.ifBlank { "NexGen Shopper" },
                email = email,
                photoUrl = photoUrl,
                role = assignedRole,
                loyaltyPoints = 100L,
                loyaltyTier = "Bronze",
                customerGroup = "Retail",
                status = "active"
            )
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun updateUserProfile(
        displayName: String,
        phone: String?,
        defaultAddress: String?
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "displayName" to displayName.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!phone.isNullOrBlank()) updates["phone"] = phone.trim()
        if (!defaultAddress.isNullOrBlank()) updates["defaultAddress"] = defaultAddress.trim()

        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeAddresses(userId: String): Flow<List<SavedAddress>> {
        val colRef = db.collection("users").document(userId).collection("addresses")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(SavedAddress::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun saveAddress(address: SavedAddress): Result<String> = runCatching {
        val uid = requireUserId()
        val id = address.id.ifBlank { "addr_${UUID.randomUUID().toString().take(8)}" }
        val docRef = db.collection("users").document(uid).collection("addresses").document(id)
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "label" to address.label.ifBlank { "Home" },
            "recipientName" to address.recipientName.trim(),
            "phone" to address.phone.trim(),
            "street" to address.street.trim(),
            "city" to address.city.trim(),
            "latitude" to address.latitude,
            "longitude" to address.longitude,
            "isDefault" to address.isDefault,
            "createdAt" to (address.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
        id
    }

    suspend fun deleteAddress(addressId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("addresses").document(addressId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeNotifications(userId: String): Flow<List<AppNotification>> {
        val colRef = db.collection("users").document(userId).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "users/$userId/notifications")
                throw error
            }
    }

    suspend fun sendNotification(
        targetUserId: String,
        title: String,
        body: String,
        type: String,
        referenceId: String? = null,
        targetRole: String? = null
    ): Result<Unit> = runCatching {
        val id = "notif_${UUID.randomUUID().toString().take(10)}"
        val docRef = db.collection("users").document(targetUserId).collection("notifications").document(id)
        val payload = hashMapOf<String, Any>(
            "userId" to targetUserId,
            "title" to title.take(120),
            "body" to body.take(400),
            "type" to type,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!referenceId.isNullOrBlank()) payload["referenceId"] = referenceId
        if (!targetRole.isNullOrBlank()) payload["targetRole"] = targetRole
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notifications").document(notifId)
        try {
            docRef.update(mapOf("isRead" to true)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeAdminRecord(userId: String): Flow<AdminRecord?> {
        val docRef = db.collection("admins").document(userId)
        return docRef.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(AdminRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.GET, docRef.path)
                emit(null)
            }
    }

    fun observeAdminGrantByEmail(email: String?): Flow<AdminRecord?> {
        val emailLower = email?.trim()?.lowercase(Locale.US).orEmpty()
        if (emailLower.isBlank()) return flowOf(null)
        val docRef = db.collection("admin_grants").document(emailLower)
        return docRef.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(AdminRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                emit(null)
            }
    }

    fun observeStaffMembership(userId: String): Flow<List<StaffMember>> {
        val query = db.collection("staff").whereEqualTo("staffUid", userId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "staff")
                emit(emptyList())
            }
    }

    fun observeStaffGrantByEmail(email: String?): Flow<StaffMember?> {
        val emailLower = email?.trim()?.lowercase(Locale.US).orEmpty()
        if (emailLower.isBlank()) return flowOf(null)
        val docRef = db.collection("staff_grants").document(emailLower)
        return docRef.snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                emit(null)
            }
    }

    companion object {
        val SUPER_ADMIN_EMAILS = setOf("nexgenphonelab@gmail.com", "mrclvictor14@gmail.com")

        fun isSuperAdminEmail(email: String?, isEmailVerified: Boolean): Boolean {
            val normalized = email?.trim()?.lowercase(Locale.US).orEmpty()
            return isEmailVerified && SUPER_ADMIN_EMAILS.contains(normalized)
        }
    }
}
