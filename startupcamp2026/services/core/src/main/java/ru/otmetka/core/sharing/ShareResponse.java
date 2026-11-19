package ru.otmetka.core.sharing;

import java.util.UUID;

/** The Share schema of api/openapi.yaml. */
record ShareResponse(UUID id, UUID planId, String invitedEmail, String role, String status) {

    static ShareResponse of(Share share) {
        return new ShareResponse(share.getId(), share.getPlanId(), share.getInvitedEmail(), share.getRole(),
                share.getStatus());
    }
}
