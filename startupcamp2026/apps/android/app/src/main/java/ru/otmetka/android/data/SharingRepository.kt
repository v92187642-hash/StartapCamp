package ru.otmetka.android.data

import java.util.UUID
import ru.otmetka.android.api.models.Share
import ru.otmetka.android.api.models.ShareCreate

/** Who may watch a plan. Always asks the server: nothing here works offline. */
class SharingRepository(private val api: ApiFactory) {

    suspend fun shares(planId: String): List<Share> = api.sharing.listShares(UUID.fromString(planId)).bodyOrThrow()

    /** The server emails an invitation link to the address. */
    suspend fun invite(planId: String, email: String): Share =
        api.sharing.inviteRelative(UUID.fromString(planId), ShareCreate(email)).bodyOrThrow()

    /** Withdraws an invitation, or closes access that was already accepted. */
    suspend fun revoke(shareId: UUID) = api.sharing.revokeShare(shareId).throwIfFailed()
}
