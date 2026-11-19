# Legal drafts

Drafts written by Claude, not by a lawyer. Nothing here has been reviewed.
Milestone M5 requires a lawyer's review before real users appear.

The files are in Russian because they are either shown to users or handed to
a Russian lawyer.

| File | What it is |
|---|---|
| `privacy-policy-draft.md` | Privacy policy to publish on the site and link from the store listing |
| `consent-text.md` | The consent shown after the first login, as implemented, with its version |
| `questions-for-lawyer.md` | What the product stores and does, and the questions the team cannot answer itself |

When the consent text changes, change it in three places together and raise
the version: `apps/web/src/pages/ConsentPage.tsx`,
`apps/android/app/src/main/res/values/strings.xml` (and
`SessionRepository.CONSENT_TEXT_VERSION`), and `consent-text.md`.
